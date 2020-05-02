// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;

import java.awt.*;

import static com.intellij.openapi.util.NlsContexts.NotificationContent;
import static com.intellij.openapi.util.NlsContexts.NotificationTitle;
import static org.jetbrains.plugins.github.util.GithubUtil.getErrorTextFromException;

public class GithubNotifications {
  private static final Logger LOG = GithubUtil.LOG;

  private static boolean isOperationCanceled(@NotNull Throwable e) {
    return e instanceof GithubOperationCanceledException ||
           e instanceof ProcessCanceledException;
  }

  public static void showInfo(@NotNull Project project,
                              @NotificationTitle @NotNull String title,
                              @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    VcsNotifier.getInstance(project).notifyImportantInfo(title, message);
  }

  public static void showWarning(@NotNull Project project,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    VcsNotifier.getInstance(project).notifyImportantWarning(title, message);
  }

  public static void showWarning(@NotNull Project project,
                                 @NotificationTitle @NotNull String title,
                                 @NotNull Exception e) {
    LOG.info(title + "; ", e);
    if (isOperationCanceled(e)) return;
    VcsNotifier.getInstance(project).notifyImportantWarning(title, getErrorTextFromException(e));
  }

  public static void showWarning(@NotNull Project project,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message,
                                 AnAction @Nullable ... actions) {
    LOG.info(title + "; " + message);
    Notification notification =
      new Notification(VcsNotifier.IMPORTANT_ERROR_NOTIFICATION.getDisplayId(), title, message, NotificationType.WARNING);
    if (actions != null) {
      for (AnAction action : actions) {
        notification.addAction(action);
      }
    }
    notification.notify(project);
  }

  public static void showError(@NotNull Project project,
                               @NotificationTitle @NotNull String title,
                               @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    VcsNotifier.getInstance(project).notifyError(title, message);
  }

  public static void showError(@NotNull Project project,
                               @NotificationTitle @NotNull String title,
                               @NotificationContent @NotNull String message,
                               @NotNull String logDetails) {
    LOG.warn(title + "; " + message + "; " + logDetails);
    VcsNotifier.getInstance(project).notifyError(title, message);
  }

  public static void showError(@NotNull Project project,
                               @NotificationTitle @NotNull String title,
                               @NotNull Throwable e) {
    LOG.warn(title + "; ", e);
    if (isOperationCanceled(e)) return;
    VcsNotifier.getInstance(project).notifyError(title, getErrorTextFromException(e));
  }

  public static void showInfoURL(@NotNull Project project,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message,
                                 @NotNull String url) {
    LOG.info(title + "; " + message + "; " + url);
    //noinspection HardCodedStringLiteral
    VcsNotifier.getInstance(project)
      .notifyImportantInfo(title, "<a href='" + url + "'>" + message + "</a>", NotificationListener.URL_OPENING_LISTENER);
  }

  public static void showWarningURL(@NotNull Project project,
                                    @NotificationTitle @NotNull String title,
                                    @NotNull String prefix,
                                    @NotNull String highlight,
                                    @NotNull String postfix,
                                    @NotNull String url) {
    LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
    //noinspection HardCodedStringLiteral
    VcsNotifier.getInstance(project).notifyImportantWarning(title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                                                            NotificationListener.URL_OPENING_LISTENER);
  }

  public static void showErrorURL(@NotNull Project project,
                                  @NotificationTitle @NotNull String title,
                                  @NotNull String prefix,
                                  @NotNull String highlight,
                                  @NotNull String postfix,
                                  @NotNull String url) {
    LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
    //noinspection HardCodedStringLiteral
    VcsNotifier.getInstance(project).notifyError(title, prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                                                 NotificationListener.URL_OPENING_LISTENER);
  }

  public static void showInfoDialog(@Nullable Project project,
                                    @NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showInfoMessage(project, message, title);
  }

  public static void showInfoDialog(@NotNull Component component,
                                    @NotificationTitle @NotNull String title,
                                    @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showInfoMessage(component, message, title);
  }

  public static void showWarningDialog(@Nullable Project project,
                                       @NotificationTitle @NotNull String title,
                                       @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showWarningDialog(project, message, title);
  }

  public static void showWarningDialog(@NotNull Component component,
                                       @NotificationTitle @NotNull String title,
                                       @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showWarningDialog(component, message, title);
  }

  public static void showErrorDialog(@Nullable Project project,
                                     @NotificationTitle @NotNull String title,
                                     @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showErrorDialog(project, message, title);
  }

  public static void showErrorDialog(@Nullable Project project,
                                     @NotificationTitle @NotNull String title,
                                     @NotNull Throwable e) {
    LOG.warn(title, e);
    if (isOperationCanceled(e)) return;
    Messages.showErrorDialog(project, getErrorTextFromException(e), title);
  }

  public static void showErrorDialog(@NotNull Component component,
                                     @NotificationTitle @NotNull String title,
                                     @NotNull Throwable e) {
    LOG.info(title, e);
    if (isOperationCanceled(e)) return;
    Messages.showErrorDialog(component, getErrorTextFromException(e), title);
  }

  public static void showErrorDialog(@NotNull Component component,
                                     @NotificationTitle @NotNull String title,
                                     @NotNull String prefix,
                                     @NotNull Exception e) {
    LOG.info(title, e);
    if (isOperationCanceled(e)) return;
    Messages.showErrorDialog(component, prefix + getErrorTextFromException(e), title);
  }

  @Messages.YesNoResult
  public static boolean showYesNoDialog(@Nullable Project project,
                                        @NotificationTitle @NotNull String title,
                                        @NotificationContent @NotNull String message) {
    return Messages.YES == Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon());
  }

  @Messages.YesNoResult
  public static boolean showYesNoDialog(@Nullable Project project,
                                        @NotificationTitle @NotNull String title,
                                        @NotificationContent @NotNull String message,
                                        @NotNull DialogWrapper.DoNotAskOption doNotAskOption) {
    return Messages.YES == Messages.showYesNoDialog(project, message, title, Messages.getQuestionIcon(), doNotAskOption);
  }

  @NotNull
  public static AnAction getConfigureAction(@NotNull Project project) {
    return NotificationAction.createSimple(GitBundle.messagePointer("action.NotificationAction.GithubNotifications.text.configure"),
                                           () -> ShowSettingsUtil.getInstance()
                                             .showSettingsDialog(project, GithubUtil.SERVICE_DISPLAY_NAME));
  }
}
