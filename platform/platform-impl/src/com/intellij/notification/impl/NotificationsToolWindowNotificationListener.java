// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexander Lobas
 */
public class NotificationsToolWindowNotificationListener implements Notifications {
  private final Project myProject;

  public NotificationsToolWindowNotificationListener() {
    this(null);
  }

  public NotificationsToolWindowNotificationListener(@Nullable Project project) {
    myProject = project;
  }

  @Override
  public void notify(@NotNull Notification notification) {
    if (Registry.is("ide.notification.action.center", false) && notification.canShowFor(myProject)) {
      NotificationsToolWindowKt.addNotification(myProject, notification);
    }
  }
}