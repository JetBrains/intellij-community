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
package org.jetbrains.plugins.github.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import git4idea.Notificator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

import static org.jetbrains.plugins.github.util.GithubUtil.getErrorTextFromException;

/**
 * @author Aleksey Pivovarov
 */
public class GithubNotifications {
  private static final Logger LOG = GithubUtil.LOG;

  private static final String GITHUB_NOTIFICATION_GROUP = "github";

  public static void showInfo(@NotNull Project project, @NotNull String title, @NotNull String message) {
    LOG.info(title + "; " + message);
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.INFORMATION);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showWarning(@NotNull Project project, @NotNull String title, @NotNull String message) {
    LOG.info(title + "; " + message);
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.WARNING);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String message) {
    LOG.info(title + "; " + message);
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull String logDetails) {
    LOG.warn(title + "; " + message + "; " + logDetails);
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, message, NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showError(@NotNull Project project, @NotNull String title, @NotNull Exception e) {
    LOG.warn(title + "; ", e);
    Notification notification = new Notification(GITHUB_NOTIFICATION_GROUP, title, getErrorTextFromException(e), NotificationType.ERROR);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showInfoURL(@NotNull Project project, @NotNull String title, @NotNull String message, @NotNull String url) {
    LOG.info(title + "; " + message + "; " + url);
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, "<a href='" + url + "'>" + message + "</a>", NotificationType.INFORMATION,
                       NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showWarningURL(@NotNull Project project,
                                    @NotNull String title,
                                    @NotNull String prefix,
                                    @NotNull String highlight,
                                    @NotNull String postfix,
                                    @NotNull String url) {
    LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                       NotificationType.WARNING, NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showErrorURL(@NotNull Project project,
                                  @NotNull String title,
                                  @NotNull String prefix,
                                  @NotNull String highlight,
                                  @NotNull String postfix,
                                  @NotNull String url) {
    LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
    Notification notification =
      new Notification(GITHUB_NOTIFICATION_GROUP, title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                       NotificationType.ERROR, NotificationListener.URL_OPENING_LISTENER);
    Notificator.getInstance(project).notify(notification);
  }

  public static void showInfoDialog(final @Nullable Project project, final @NotNull String title, final @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showInfoMessage(project, message, title);
  }

  public static void showInfoDialog(final @NotNull Component component, final @NotNull String title, final @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showInfoMessage(component, message, title);
  }

  public static void showWarningDialog(final @Nullable Project project, final @NotNull String title, final @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showWarningDialog(project, message, title);
  }

  public static void showWarningDialog(final @NotNull Component component, final @NotNull String title, final @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showWarningDialog(component, message, title);
  }

  public static void showErrorDialog(final @Nullable Project project, final @NotNull String title, final @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showErrorDialog(project, message, title);
  }

  public static void showErrorDialog(final @Nullable Project project, final @NotNull String title, final @NotNull Exception e) {
    LOG.warn(title, e);
    Messages.showErrorDialog(project, getErrorTextFromException(e), title);
  }

  public static void showErrorDialog(final @NotNull Component component, final @NotNull String title, final @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showErrorDialog(component, message, title);
  }

  public static void showErrorDialog(final @NotNull Component component, final @NotNull String title, final @NotNull Exception e) {
    LOG.info(title, e);
    Messages.showInfoMessage(component, getErrorTextFromException(e), title);
  }

  public static int showYesNoDialog(final @Nullable Project project, final @NotNull String title, final @NotNull String message) {
    return Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon());
  }
}
