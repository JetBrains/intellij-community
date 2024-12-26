// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.notification;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

/**
 * @author Vladislav.Soroka
 */
public class GotoSourceNotificationCallback extends NotificationListener.Adapter {
  public static final String ID = "goto_source";

  private final NotificationData myNotificationData;
  private final Project myProject;

  public GotoSourceNotificationCallback(NotificationData notificationData, Project project) {
    myNotificationData = notificationData;
    myProject = project;
  }

  @Override
  protected void hyperlinkActivated(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
    GradleNotificationCallbackUtil.navigateByNotificationData(myProject, myNotificationData);
  }
}
