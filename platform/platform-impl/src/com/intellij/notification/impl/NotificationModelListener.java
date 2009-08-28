package com.intellij.notification.impl;

import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public interface NotificationModelListener<T extends Notification> {
  void notificationsAdded(@NotNull final T... notification);
  void notificationsRemoved(@NotNull final T... notification);
  void notificationsArchived(@NotNull final T... notification);
}
