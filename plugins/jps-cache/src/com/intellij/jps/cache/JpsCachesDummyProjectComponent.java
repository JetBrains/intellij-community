package com.intellij.jps.cache;

import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.server.BuildManager;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import static com.intellij.jps.cache.ui.JpsLoaderNotifications.STICKY_NOTIFICATION_GROUP;

public class JpsCachesDummyProjectComponent implements ProjectComponent {
  private static final String NOT_ASK_AGAIN = "JpsCaches.NOT_ASK_AGAIN";
  private final Project myProject;
  private final PropertiesComponent myPropertiesComponent;
  private final CompilerWorkspaceConfiguration myWorkspaceConfiguration;

  public JpsCachesDummyProjectComponent(@NotNull Project project) {
    myProject = project;
    myPropertiesComponent = PropertiesComponent.getInstance(project);
    myWorkspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(myProject);
  }

  @Override
  public void projectOpened() {
    if (myWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE && !myPropertiesComponent.getBoolean(NOT_ASK_AGAIN, false)) {
      Notification notification = STICKY_NOTIFICATION_GROUP.createNotification("Automatic project build enabled",
                                                                               "Make project automatically enabled, it can affect portable caches",
                                                                               NotificationType.WARNING, null);
      notification.addAction(NotificationAction.createSimple("Disable Property", () -> {
        myWorkspaceConfiguration.MAKE_PROJECT_ON_SAVE = false;
        BuildManager.getInstance().clearState(myProject);
        notification.expire();
      }));
      notification.addAction(NotificationAction.createSimple("Don't Ask Again", () -> {
        myPropertiesComponent.setValue(NOT_ASK_AGAIN, true);
        notification.expire();
      }));
      Notifications.Bus.notify(notification, myProject);
    }
  }
}
