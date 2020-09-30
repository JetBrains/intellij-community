// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * @author michael.golubev
 */
public class CloudNotifier {

  private final String myNotificationDisplayId;

  public CloudNotifier(String notificationDisplayId) {
    myNotificationDisplayId = notificationDisplayId;
  }

  public void showMessage(@NlsContexts.NotificationContent String message, MessageType messageType) {
    showMessage(message, messageType, null);
  }

  public Notification showMessage(@NlsContexts.NotificationContent String message,
                                  MessageType messageType,
                                  @Nullable NotificationListener listener) {
    NotificationGroup notificationGroup = findOrCreateBaloonGroup(myNotificationDisplayId);
    Notification notification = notificationGroup.createNotification("", message, messageType.toNotificationType(), listener);
    notification.notify(null);
    return notification;
  }

  @NotNull
  private static NotificationGroup findOrCreateBaloonGroup(@NotNull String displayId) {
    return Optional
      .ofNullable(NotificationGroup.findRegisteredGroup(displayId))
      .orElseGet(() -> NotificationGroup.balloonGroup(displayId));
  }
}
