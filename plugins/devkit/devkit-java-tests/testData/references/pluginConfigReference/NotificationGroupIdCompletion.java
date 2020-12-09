import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;

public class NotificationGroupIdCompletion {
  public static void main(String[] args) {
    new Notification("<caret>", null, NotificationType.INFORMATION);
  }
}