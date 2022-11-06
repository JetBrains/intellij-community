// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.project;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;

/**
 * @author Vladislav.Soroka
 */
public class GradleNotification {
  public static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup("Gradle Notification Group");

  private GradleNotification() {
  }
}
