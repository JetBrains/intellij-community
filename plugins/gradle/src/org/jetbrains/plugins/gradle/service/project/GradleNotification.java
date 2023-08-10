// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;

/**
 * @author Vladislav.Soroka
 */
public final class GradleNotification {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Notification Group");

  private GradleNotification() {
  }
}
