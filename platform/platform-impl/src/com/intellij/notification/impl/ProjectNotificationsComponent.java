/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

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
    final Collection<Notification> collection = NotificationsManagerImpl.getNotificationsManagerImpl().getByType(null, myProject);
    for (final Notification notification : collection) {
      final Balloon balloon = notification.getBalloon();
      if (balloon != null) balloon.hide();
    }
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
