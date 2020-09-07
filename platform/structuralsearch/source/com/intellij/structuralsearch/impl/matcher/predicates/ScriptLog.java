// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher.predicates;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.structuralsearch.plugin.ui.UIUtil;

import static com.intellij.structuralsearch.impl.matcher.predicates.ScriptSupport.UUID;

/**
 * @author Bas Leijdekkers
 */
public class ScriptLog {

  public static final String SCRIPT_LOG_VAR_NAME = "__log__";

  private final Project myProject;

  public ScriptLog(Project project) {
    myProject = project;
  }

  public void info(Object message) {
    log(message, NotificationType.INFORMATION);
  }

  public void warn(Object message) {
    log(message, NotificationType.WARNING);
  }

  public void error(Object message) {
    log(message, NotificationType.ERROR);
  }

  private void log(Object message, NotificationType type) {
    final StackTraceElement[] stackTrace = new Throwable().getStackTrace();
    String location = "";
    for (StackTraceElement e : stackTrace) {
      final String methodName = e.getMethodName();
      if ("run".equals(methodName)) {
        final String fileName = e.getFileName();
        assert fileName != null;
        location = "(" + StringUtil.replace(fileName, UUID + ".groovy", "") + ":" + e.getLineNumber() + ") ";
        break;
      }
    }
    final NotificationGroup notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup(UIUtil.SSR_NOTIFICATION_GROUP_ID);
    notificationGroup.createNotification(location + message, type).notify(myProject);
  }
}
