package com.intellij.notification.impl;

import com.intellij.notification.Notifications;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ProjectNotificationsComponent implements Notifications, ProjectComponent {
  private Project myProject;

  public ProjectNotificationsComponent(final Project project) {
    myProject = project;
  }

  public void projectOpened() {
    if (isDummyEnvironment()) {
      return;
    }

    myProject.getMessageBus().connect().subscribe(TOPIC, this);
    Disposer.register(myProject, new Disposable() {
      public void dispose() {
        NotificationsManagerImpl.getNotificationsManagerImpl().clear(myProject);
      }
    });
  }

  public void notify(@NotNull Notification notification) {
    NotificationsManagerImpl.getNotificationsManagerImpl().doNotify(notification, null, myProject);
  }

  public void notify(@NotNull Notification notification, @NotNull NotificationDisplayType defaultDisplayType) {
    NotificationsManagerImpl.getNotificationsManagerImpl().doNotify(notification, defaultDisplayType, myProject);
  }

  public void projectClosed() {
  }

  private static boolean isDummyEnvironment() {
    final Application application = ApplicationManager.getApplication();
    return application.isUnitTestMode() || application.isCommandLine();
  }

  @NotNull
  public String getComponentName() {
    return "Project Notifications";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
    myProject = null;
  }
}
