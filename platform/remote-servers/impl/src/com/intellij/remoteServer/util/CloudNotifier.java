// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.remoteServer.util;

import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.NlsContexts;

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
    NotificationGroup group = Optional
      .ofNullable(NotificationGroup.findRegisteredGroup(myNotificationDisplayId))
      .orElseGet(() -> NotificationGroup.balloonGroup(myNotificationDisplayId));
    group.createNotification(message, messageType).notify(null);
  }
}
