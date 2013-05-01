package org.jetbrains.plugins.gradle.notification;

import com.intellij.notification.Notification;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.ui.EditorNotifications;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettingsListener;
import org.jetbrains.plugins.gradle.config.GradleSettingsListenerAdapter;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.concurrent.atomic.AtomicReference;

/**
 * This class is responsible for notifying about the gradle config changes.
 * <p/>
 * One example use-case is a situation when a user resets gradle home (e.g. project config dir has been (re)moved). We need to
 * explain that we can't use the integration until gradle is properly configured then.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 3/21/12 4:04 PM
 */
public class GradleConfigNotificationManager {
  
  // TODO den implement
//  @NotNull private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.toolWindowGroup(
//    ExternalSystemBundle.message("gradle.notification.group.display.warning"), GradleConstants.TOOL_WINDOW_ID, true
//  );

  @NotNull private final AtomicReference<Notification> myNotification = new AtomicReference<Notification>();

  @NotNull private final GradleInstallationManager myLibraryManager;
  @NotNull private final Project                   myProject;

  public GradleConfigNotificationManager(@NotNull final Project project, @NotNull GradleInstallationManager manager) {
    myProject = project;
    myLibraryManager = manager;
    final GradleSettingsListenerAdapter handler = new GradleSettingsListenerAdapter() {
      public void onGradleHomeChange(@Nullable String oldPath, @Nullable String newPath) {
        processGradleHomeChange();
      }
    };
    project.getMessageBus().connect(project).subscribe(GradleSettingsListener.TOPIC, handler);
    ProjectManager.getInstance().getDefaultProject().getMessageBus().connect(project).subscribe(GradleSettingsListener.TOPIC, handler);
  }

  private void processGradleHomeChange() {
    EditorNotifications.getInstance(myProject).updateAllNotifications();
    
    // TODO den implement
    //if (!GradleUtil.isGradleAvailable(myProject)) {
    //  return;
    //}
    //
    //final Notification notification = myNotification.get();
    //if (notification != null && myNotification.compareAndSet(notification, null)) {
    //  notification.expire();
    //}
  }

  public void processRefreshError(@NotNull String message) {
    // TODO den implement
//    final Notification notification = NOTIFICATION_GROUP.createNotification(
//      ExternalSystemBundle.message("gradle.notification.refresh.fail.description", message),
//      ExternalSystemBundle.message("gradle.notification.action.show.settings"),
//      NotificationType.WARNING,
//      new NotificationListener() {
//        @Override
//        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
//          if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
//            return;
//          }
//          ShowSettingsUtil.getInstance().editConfigurable(myProject, new GradleConfigurable(myProject, myLibraryManager));
//        }
//      }
//    );
//
//    applyNotification(notification);
  }
  
  public void processUnknownGradleHome() {
    // TODO den implement
//    final Notification notification = NOTIFICATION_GROUP.createNotification(
//      ExternalSystemBundle.message("gradle.notification.gradle.home.undefined.description"),
//      ExternalSystemBundle.message("gradle.notification.action.show.settings"),
//      NotificationType.WARNING,
//      new NotificationListener() {
//        @Override
//        public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
//          if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
//            return;
//          }
//          ShowSettingsUtil.getInstance().editConfigurable(myProject, new GradleConfigurable(myProject, myLibraryManager));
//        }
//      }
//    );
//
//    applyNotification(notification);
  }

  private void applyNotification(@NotNull Notification notification) {
    final Notification oldNotification = myNotification.get();
    if (oldNotification != null && myNotification.compareAndSet(oldNotification, null)) {
      oldNotification.expire();
    }
    if (!myNotification.compareAndSet(null, notification)) {
      notification.expire();
      return;
    }

    notification.notify(myProject);
  }
}
