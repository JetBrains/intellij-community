import com.intellij.notification.Notification;
import com.intellij.notification.NotificationBuilder;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;

public class NotificationGroupId {
  public static void main(String[] args) {
    new Notification("my.balloon", null, NotificationType.INFORMATION);
    new NotificationBuilder("my.balloon", "Content", NotificationType.WARNING);
    NotificationGroupManager.getInstance().getNotificationGroup("my.balloon");

    new Notification("<error descr="Cannot resolve notification group id 'INVALID_VALUE'">INVALID_VALUE</error>", null, NotificationType.INFORMATION);
    new NotificationBuilder("<error descr="Cannot resolve notification group id 'INVALID_VALUE'">INVALID_VALUE</error>", "Content", NotificationType.WARNING);
    NotificationGroupManager.getInstance().getNotificationGroup("<error descr="Cannot resolve notification group id 'INVALID_VALUE'">INVALID_VALUE</error>");
  }
}