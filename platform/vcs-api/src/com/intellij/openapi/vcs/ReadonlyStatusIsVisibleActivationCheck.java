// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;

/**
 * @author irengrig
 */
public class ReadonlyStatusIsVisibleActivationCheck {
  public static void check(final Project project, final String vcsName) {
    if (SystemInfoRt.isUnix && "root".equals(System.getenv("USER"))) {
      Notifications.Bus.notify(new Notification(vcsName, vcsName + ": can not see read-only status",
          "You are logged as <b>root</b>, that's why:<br><br>- " + ApplicationNamesInfo.getInstance().getFullProductName() + " can not see read-only status of files.<br>" +
          "- All files are treated as writeable.<br>- Automatic file checkout on modification is impossible.", NotificationType.WARNING), project);
    }
  }
}
