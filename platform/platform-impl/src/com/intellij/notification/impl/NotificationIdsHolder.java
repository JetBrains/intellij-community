// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;

/**
 * Class that provides constant list of possible notification ids.
 * If extension is registered in the platform or in a plugin built with IntelliJ Ultimate,
 * these ids will be registered in statistic metadata repository automatically.
 *
 * Otherwise, create a YT issue in FUS project or use com.intellij.notification.impl.NotificationGroupEP#notificationIds
 */
@ApiStatus.Internal
public interface NotificationIdsHolder {
  ExtensionPointName<NotificationIdsHolder> EP_NAME = ExtensionPointName.create("com.intellij.statistics.notificationIdsHolder");

  /**
   * List of notificationIds which should be recorded in feature usage statistics.
   * @see Notification#displayId
   */
  List<String> getNotificationIds();
}