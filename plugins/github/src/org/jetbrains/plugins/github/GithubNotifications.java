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
import git4idea.Notificator;
import org.jetbrains.annotations.NotNull;

import java.net.URL;

/**
 * @author Aleksey Pivovarov
 */
public class GithubNotifications {
  private static final Logger LOG = GithubUtil.LOG;

  private static final String GITHUB_NOTIFICATION_GROUP = "github";

  public static void showInfo(@NotNull Project project, @NotNull String title, @NotNull String message) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.INFORMATION);
    Notificator.getInstance(project).notify(notification);
    LOG.info(title + "; " + message);
  }

  public static void showWarning(@NotNull Project project, @NotNull String title, @NotNull String message) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.WARNING);
    Notificator.getInstance(project).notify(notification);
    LOG.warn(title + "; " + message);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String message) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
    LOG.warn(title + "; " + message);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull String logDetails) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
    LOG.warn(title + "; " + message + "; " + logDetails);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull Exception e) {
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, e.getMessage(), NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
    LOG.warn(title + "; ", e);
  }

  public static void showInfoURL(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull String url) {
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, "<a href='" + url + "'>" + message + "</a>", NotificationType.INFORMATION,
                       NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
    LOG.info(title + "; " + message + "; " + url);
  }

  public static void showWarningURL(@NotNull Project project,
                                    @NotNull String title,
                                    @NotNull String prefix,
                                    @NotNull String highlight,
                                    @NotNull String postfix,
                                    @NotNull String url) {
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                       NotificationType.WARNING, NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
    LOG.warn(title + "; " + prefix + highlight + postfix + "; " + url);
  }

  public static void showErrorURL(@NotNull Project project,
                                  @NotNull String title,
                                  @NotNull String prefix,
                                  @NotNull String highlight,
                                  @NotNull String postfix,
                                  @NotNull String url) {
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                       NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
    LOG.warn(title + "; " + prefix + highlight + postfix + "; " + url);
  }

  public static void showInfoDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    Messages.showInfoMessage(project, message, title);
    LOG.info(title + "; " + message);
  }

  public static void showWarningDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    Messages.showWarningDialog(project, message, title);
    LOG.warn(title + "; " + message);
  }

  public static void showErrorDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    Messages.showErrorDialog(project, message, title);
    LOG.warn(title + "; " + message);
  }

  public static int showYesNoDialog(final @NotNull Project project, final @NotNull String title, final @NotNull String message) {
    return Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon());
  }
}
