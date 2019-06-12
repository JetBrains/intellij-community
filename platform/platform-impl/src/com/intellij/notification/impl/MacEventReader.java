// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationsAdapter;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

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
      getService().submit(() -> {
        try {
          Runtime.getRuntime().exec("say " + copy).waitFor();
        }
        catch (IOException | InterruptedException e) {
          LOG.warn(e);
        }
      });
    }
  }

  private static synchronized ExecutorService getService() {
    if (ourService == null) {
      ourService = ConcurrencyUtil.newSingleThreadExecutor("Mac event reader");
    }
    return ourService;
  }

  public static class ProjectTracker {
    public ProjectTracker(@NotNull final Project project) {
      project.getMessageBus().connect().subscribe(Notifications.TOPIC, ourNotificationAdapter);
    }
  }
}


