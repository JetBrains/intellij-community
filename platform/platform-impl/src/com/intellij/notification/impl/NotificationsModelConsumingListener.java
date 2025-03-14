// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class NotificationsModelConsumingListener implements Notifications {
  private final Project myProject;

  public NotificationsModelConsumingListener() {
    this(null);
  }

  public NotificationsModelConsumingListener(@Nullable Project project) {
    myProject = project;
  }

  @Override
  public void notify(@NotNull Notification notification) {
    ApplicationNotificationsModel.addNotification(myProject, notification);
  }
}