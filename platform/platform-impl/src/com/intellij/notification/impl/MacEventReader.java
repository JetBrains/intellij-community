// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl;

import com.intellij.notification.Notification;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.Executor;

final class MacEventReader implements Notifications {
  private static final int MAX_MESSAGE_LENGTH = 100;
  private static final Logger LOG = Logger.getInstance(MacEventReader.class);

  @Override
  public void notify(@NotNull Notification notification) {
    if (!NotificationsConfigurationImpl.getSettings(notification.getGroupId()).isShouldReadAloud() ||
        NotificationsAnnouncerKt.isNotificationAnnouncerEnabled()) {
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
        if (sb.length() + word.length() >= MAX_MESSAGE_LENGTH - 1) {
          break;
        }
        if (!sb.isEmpty()) {
          sb.append(' ');
        }
        sb.append(word);
      }
      message = sb.toString();
    }

    if (!message.isEmpty()) {
      String copy = message;
      ExecutorHolder.ourService.execute(() -> {
        try {
          Runtime.getRuntime().exec("say " + copy).waitFor();
        }
        catch (IOException | InterruptedException e) {
          LOG.warn(e);
        }
      });
    }
  }
}

final class ExecutorHolder {
  static final Executor ourService = ConcurrencyUtil.newSingleThreadExecutor("Mac event reader");
}

