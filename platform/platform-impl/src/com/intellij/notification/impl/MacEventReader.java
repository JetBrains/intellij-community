/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.AbstractProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class MacEventReader {
  private static final int MAX_MESSAGE_LENGTH = 100;
  private static final Logger LOG = Logger.getInstance("#com.intellij.notification.impl.MacEventReader");
  private static final NotificationsAdapter ourNotificationAdapter = new NotificationsAdapter() {
    @Override
    public void notify(@NotNull Notification notification) {
      process(notification);
    }
  };

  private static ExecutorService ourService = null;

  MacEventReader() {
    if (SystemInfo.isMac) {
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(Notifications.TOPIC, ourNotificationAdapter);
    }
  }

  private static void process(Notification notification) {
    if (!NotificationsConfigurationImpl.getSettings(notification.getGroupId()).isShouldReadAloud()) {
      return;
    }
    String message = notification.getTitle();
    if (message.isEmpty()) {
      message = notification.getContent();
    }
    message = StringUtil.stripHtml(message, false);
    if (message.length() > MAX_MESSAGE_LENGTH) {
      String[] words = message.split("\\s");
      StringBuilder sb = new StringBuilder();
      for (String word : words) {
        if (sb.length() + word.length() >= MAX_MESSAGE_LENGTH - 1) break;
        if (sb.length() > 0) sb.append(' ');
        sb.append(word);
      }
      message = sb.toString();
    }

    if (!message.isEmpty()) {
      final String copy = message;
      getService().submit(new Runnable() {
        @Override
        public void run() {
          try {
            Runtime.getRuntime().exec("say " + copy).waitFor();
          }
          catch (IOException e) {
            LOG.warn(e);
          }
          catch (InterruptedException e) {
            LOG.warn(e);
          }
        }
      });
    }
  }

  private static synchronized ExecutorService getService() {
    if (ourService == null) {
      ourService = Executors.newSingleThreadExecutor(ConcurrencyUtil.newNamedThreadFactory("Mac event reader"));
    }
    return ourService;
  }

  public static class ProjectTracker extends AbstractProjectComponent {
    public ProjectTracker(@NotNull final Project project) {
      super(project);
      project.getMessageBus().connect(project).subscribe(Notifications.TOPIC, ourNotificationAdapter);
    }
  }
}


