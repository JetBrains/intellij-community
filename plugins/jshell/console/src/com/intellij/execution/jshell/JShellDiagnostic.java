// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.jshell;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;

/**
 * @author Eugene Zhuravlev
 */
public final class JShellDiagnostic {
  private static final String NOTIFICATION_GROUP = "JSHELL_NOTIFICATIONS";
  static final @NlsSafe String TITLE = "JShell";

  public static void notifyInfo(final @NlsContexts.NotificationContent String text, final Project project) {
    new Notification(NOTIFICATION_GROUP, TITLE, text, NotificationType.INFORMATION).notify(project);
  }

  public static void notifyError(Exception ex, final Project project) {
    new Notification(NOTIFICATION_GROUP, TITLE, StringUtil.notNullize(ex.getMessage(), JShellBundle.message("jshell.internal.error")), NotificationType.ERROR).notify(project);
  }
}