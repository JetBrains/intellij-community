// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class DslErrorReporter {

  static final NotificationGroup NOTIFICATION_GROUP = NotificationGroupManager.getInstance().getNotificationGroup(
    "Groovy DSL errors"
  );

  public static DslErrorReporter getInstance() {
    return ApplicationManager.getApplication().getService(DslErrorReporter.class);
  }

  public abstract void invokeDslErrorPopup(Throwable e, final Project project, @NotNull VirtualFile vfile);
}
