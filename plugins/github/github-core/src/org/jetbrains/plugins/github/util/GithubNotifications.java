// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.util;

import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.i18n.GitBundle;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.exceptions.GithubOperationCanceledException;

import static com.intellij.openapi.util.NlsContexts.NotificationContent;
import static com.intellij.openapi.util.NlsContexts.NotificationTitle;
import static org.jetbrains.plugins.github.util.GithubUtil.getErrorTextFromException;

public final class GithubNotifications {
  private static final Logger LOG = GithubUtil.LOG;

  private static boolean isOperationCanceled(@NotNull Throwable e) {
    return e instanceof GithubOperationCanceledException ||
           e instanceof ProcessCanceledException;
  }

  public static void showInfo(@NotNull Project project,
                              @NonNls @Nullable String displayId,
                              @NotificationTitle @NotNull String title,
                              @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    VcsNotifier.getInstance(project).notifyImportantInfo(displayId, title, message);
  }

  public static void showWarning(@NotNull Project project,
                                 @NonNls @Nullable String displayId,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    VcsNotifier.getInstance(project).notifyImportantWarning(displayId, title, message);
  }

  public static void showError(@NotNull Project project,
                               @NonNls @Nullable String displayId,
                               @NotificationTitle @NotNull String title,
                               @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    VcsNotifier.getInstance(project).notifyError(displayId, title, message);
  }

  public static void showError(@NotNull Project project,
                               @NonNls @Nullable String displayId,
                               @NotificationTitle @NotNull String title,
                               @NotificationContent @NotNull String message,
                               @NotNull String logDetails) {
    LOG.warn(title + "; " + message + "; " + logDetails);
    VcsNotifier.getInstance(project).notifyError(displayId, title, message);
  }

  public static void showError(@NotNull Project project,
                               @NonNls @Nullable String displayId,
                               @NotificationTitle @NotNull String title,
                               @NotNull Throwable e) {
    LOG.warn(title + "; ", e);
    if (isOperationCanceled(e)) return;
    VcsNotifier.getInstance(project).notifyError(displayId, title, getErrorTextFromException(e));
  }

  public static void showInfoURL(@NotNull Project project,
                                 @NonNls @Nullable String displayId,
                                 @NotificationTitle @NotNull String title,
                                 @NotificationContent @NotNull String message,
                                 @NotNull String url) {
    LOG.info(title + "; " + message + "; " + url);
    VcsNotifier.getInstance(project)
      .notifyImportantInfo(displayId, title, HtmlChunk.link(url, message).toString(), NotificationListener.URL_OPENING_LISTENER);
  }

  public static void showWarningURL(@NotNull Project project,
                                    @NonNls @Nullable String displayId,
                                    @NotificationTitle @NotNull String title,
                                    @NotNull String prefix,
                                    @NotNull String highlight,
                                    @NotNull String postfix,
                                    @NotNull String url) {
    LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
    //noinspection HardCodedStringLiteral
    VcsNotifier.getInstance(project).notifyImportantWarning(displayId, title,
                                                            prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                                                            NotificationListener.URL_OPENING_LISTENER);
  }

  public static void showErrorURL(@NotNull Project project,
                                  @NonNls @Nullable String displayId,
                                  @NotificationTitle @NotNull String title,
                                  @NotNull String prefix,
                                  @NotNull String highlight,
                                  @NotNull String postfix,
                                  @NotNull String url) {
    LOG.info(title + "; " + prefix + highlight + postfix + "; " + url);
    //noinspection HardCodedStringLiteral
    VcsNotifier.getInstance(project).notifyError(displayId, title,
                                                 prefix + "<a href='" + url + "'>" + highlight + "</a>" + postfix,
                                                 NotificationListener.URL_OPENING_LISTENER);
  }

  public static void showWarningDialog(@Nullable Project project,
                                       @NotificationTitle @NotNull String title,
                                       @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showWarningDialog(project, message, title);
  }

  public static void showErrorDialog(@Nullable Project project,
                                     @NotificationTitle @NotNull String title,
                                     @NotificationContent @NotNull String message) {
    LOG.info(title + "; " + message);
    Messages.showErrorDialog(project, message, title);
  }

  @Messages.YesNoResult
  public static boolean showYesNoDialog(@Nullable Project project,
                                        @NotificationTitle @NotNull String title,
                                        @NotificationContent @NotNull String message) {
    return MessageDialogBuilder.yesNo(title, message).ask(project);
  }

  @Messages.YesNoResult
  public static boolean showYesNoDialog(@Nullable Project project,
                                        @NotificationTitle @NotNull String title,
                                        @NotificationContent @NotNull String message,
                                        @NotNull DoNotAskOption doNotAskOption) {
    return MessageDialogBuilder.yesNo(title, message)
      .icon(Messages.getQuestionIcon())
      .doNotAsk(doNotAskOption)
      .ask(project);
  }

  public static @NotNull AnAction getConfigureAction(@NotNull Project project) {
    return NotificationAction.createSimple(GitBundle.messagePointer("action.NotificationAction.GithubNotifications.text.configure"),
                                           () -> ShowSettingsUtil.getInstance()
                                             .showSettingsDialog(project, GithubUtil.SERVICE_DISPLAY_NAME));
  }
}
