package pl.adamd.chatmychat.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final SimpMessagingTemplate messagingTemplate;

    private final Set<User> onlineUsers = new LinkedHashSet<>();

    public WebSocketController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat")
    public void handleChatMessage(@Payload Message message, SimpMessageHeaderAccessor headerAccessor) {
        messagingTemplate.convertAndSend("/topic/all/messages", message);

        if (Action.JOINED.equals(message.action())) {
            String userDestination = String.format("/topic/%s/messages", message.user()
                                                                                .id());
            onlineUsers.forEach(user -> {
                Message newMessage = new Message(user, null, Action.JOINED, null);
                messagingTemplate.convertAndSend(userDestination, newMessage);
            });

            Objects.requireNonNull(headerAccessor.getSessionAttributes())
                   .put("user", message.user());
            onlineUsers.add(message.user());
        }
    }

    @EventListener
    public void handleSessionDisconnectEvent(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());

        Map<String, Object> sessionAttributes = headerAccessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.error("Unable to get the user as headerAccessor.getSessionAttributes() is null");
            return;
        }

        User user = (User) sessionAttributes.get("user");
        if (user == null) {
            return;
        }
        onlineUsers.remove(user);

        Message message = new Message(user, "", Action.LEFT, Instant.now());
        messagingTemplate.convertAndSend("/topic/all/messages", message);
    }
}
