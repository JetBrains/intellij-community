/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import git4idea.Notificator;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;

/**
 * @author Aleksey Pivovarov
 */
public class GithubNotifications {
  private static final Logger LOG = Logger.getInstance(GithubNotifications.class);

  private static final String GITHUB_NOTIFICATION_GROUP = "github";

  public static void showInfo(@NotNull Project project, @NotNull String title, @NotNull String message) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.INFORMATION);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showWarning(@NotNull Project project, @NotNull String title, @NotNull String message) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.WARNING);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String message) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull IOException e) {
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, GithubUtil.getErrorTextFromException(e), NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showInfoURL(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull String url) {
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, "<a href='" + url + "'>" + message + "</a>", NotificationType.INFORMATION,
                       NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showInfoDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    if (EventQueue.isDispatchThread()) {
      Messages.showInfoMessage(project, message, title);
    }
    else {
      try {
        EventQueue.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            Messages.showInfoMessage(project, message, title);
          }
        });
      }
      catch (Exception e) {
        LOG.error("Notification error", e);
      }
    }
  }

  public static void showWarningDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    if (EventQueue.isDispatchThread()) {
      Messages.showWarningDialog(project, message, title);
    }
    else {
      try {
        EventQueue.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            Messages.showWarningDialog(project, message, title);
          }
        });
      }
      catch (Exception e) {
        LOG.error("Notification error", e);
      }
    }
  }

  public static void showErrorDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    if (EventQueue.isDispatchThread()) {
      Messages.showErrorDialog(project, message, title);
    }
    else {
      try {
        EventQueue.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            Messages.showErrorDialog(project, message, title);
          }
        });
      }
      catch (Exception e) {
        LOG.error("Notification error", e);
      }
    }
  }

  public static int showYesNoDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    if (EventQueue.isDispatchThread()) {
      return Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon());
    }
    else {
      try {
        final Ref<Integer> result = new Ref<Integer>();
        EventQueue.invokeAndWait(new Runnable() {
          @Override
          public void run() {
            result.set(Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon()));
          }
        });
        return result.get();
      }
      catch (Exception e) {
        LOG.error("Notification error", e);
        return Messages.CANCEL;
      }
    }
  }
}
