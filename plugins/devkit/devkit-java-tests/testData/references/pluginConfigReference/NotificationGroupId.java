import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;

public class NotificationGroupId {
  public static void main(String[] args) {
    new Notification("my.balloon", "Content", NotificationType.INFORMATION);
    NotificationGroupManager.getInstance().getNotificationGroup("my.balloon");

    new Notification("<error descr="Cannot resolve notification group id 'INVALID_VALUE'">INVALID_VALUE</error>", "Content", NotificationType.INFORMATION);
    NotificationGroupManager.getInstance().getNotificationGroup("<error descr="Cannot resolve notification group id 'INVALID_VALUE'">INVALID_VALUE</error>");
  }
}
