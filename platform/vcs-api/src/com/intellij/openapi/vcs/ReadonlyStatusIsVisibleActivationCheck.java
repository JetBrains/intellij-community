// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

/**
 * @author irengrig
 */
public final class ReadonlyStatusIsVisibleActivationCheck {
  public static void check(final Project project, final String vcsName) {
    if (SystemInfo.isUnix && "root".equals(System.getenv("USER"))) {
      String message = VcsBundle.message("message.read.only.status.title", vcsName);
      String fullProductName = ApplicationNamesInfo.getInstance().getFullProductName();
      String content = VcsBundle.message("message.read.only.status.content", fullProductName);
      Notifications.Bus.notify(new Notification(vcsName, message, content, NotificationType.WARNING), project);
    }
  }
}
