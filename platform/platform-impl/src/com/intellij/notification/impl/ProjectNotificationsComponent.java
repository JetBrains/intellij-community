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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class ProjectNotificationsComponent implements Notifications, ProjectComponent {
  private Project myProject;

  public ProjectNotificationsComponent(final Project project) {
    myProject = project;

    if (isDummyEnvironment()) {
      return;
    }

    myProject.getMessageBus().connect().subscribe(TOPIC, this);
  }

  public void projectOpened() {
  }

  public void notify(@NotNull Notification notification) {
    NotificationsManagerImpl.doNotify(notification, null, myProject);
  }

  @Override
  public void register(@NotNull String groupDisplayName, @NotNull NotificationDisplayType defaultDisplayType) {
  }

  @Override
  public void register(@NotNull String groupDisplayName,
                       @NotNull NotificationDisplayType defaultDisplayType,
                       boolean shouldLog) {
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
