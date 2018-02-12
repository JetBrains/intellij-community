/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.remoteServer.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.ui.MessageType;
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

  public void showMessage(String message, MessageType messageType) {
    showMessage(message, messageType, null);
  }

  public Notification showMessage(String message, MessageType messageType, @Nullable NotificationListener listener) {
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
