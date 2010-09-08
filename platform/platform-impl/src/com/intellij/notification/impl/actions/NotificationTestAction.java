/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.notification.impl.actions;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.util.messages.MessageBus;

import javax.swing.event.HyperlinkEvent;

/**
 * @author spleaner
 */
public class NotificationTestAction extends AnAction implements DumbAware {

  public static final String TEST_GROUP_ID = "Test Notification";

  public NotificationTestAction() {
    super("Add Test Notification");
  }

  public void actionPerformed(final AnActionEvent e) {
    final Project project = PlatformDataKeys.PROJECT.getData(e.getDataContext());
    final MessageBus messageBus = project == null ? ApplicationManager.getApplication().getMessageBus() : project.getMessageBus();

    final long l = System.currentTimeMillis();
    final NotificationType type;
    if (l % 3 == 0) {
      type = NotificationType.ERROR;
    } else if (l % 5 == 0) {
      type = NotificationType.WARNING;
    } else {
      type = NotificationType.INFORMATION;
    }

    final NotificationListener listener = new NotificationListener() {
      public void hyperlinkUpdate(Notification n, HyperlinkEvent e) {
        n.expire();
      }
    };

    final Notification notification = new Notification(TEST_GROUP_ID, "This is a test notification",
      "You can close this very very very very long notification by clicking <a href=\"close\">this link</a>. Long long long long. It should be long. Very long. Too long.",
      type, listener);

    messageBus.syncPublisher(Notifications.TOPIC).notify(notification);
  }
}
