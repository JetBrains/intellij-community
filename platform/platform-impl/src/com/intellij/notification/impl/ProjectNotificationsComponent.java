package com.intellij.notification.impl;

import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.ex.StatusBarEx;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ProjectNotificationsComponent extends AbstractProjectComponent {
  public ProjectNotificationsComponent(final Project project) {
    super(project);
  }

  public void projectOpened() {
    if (isDummyEnvironment()) {
      return;
    }

    StatusBarEx statusBar = (StatusBarEx)WindowManager.getInstance().getStatusBar(myProject);
    myProject.getMessageBus().connect().subscribe(Notifications.TOPIC, statusBar.getNotificationArea());
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        NotificationsManager.getNotificationsManager().clear(myProject);
      }
    });
  }

  private static boolean isDummyEnvironment() {
    final Application application = ApplicationManager.getApplication();
    return application.isUnitTestMode() || application.isCommandLine();
  }

  @NotNull
  public String getComponentName() {
    return "Project Notifications";
  }
}
