package com.intellij.notification.impl;

import org.jetbrains.annotations.NotNull;
import com.intellij.notification.Notification;

/**
 * @author spleaner
 */
public interface NotificationModelListener {
  void notificationsAdded(@NotNull Notification... notification);
  void notificationsRemoved(@NotNull Notification... notification);
  void notificationsRead(@NotNull Notification... notification);
}
