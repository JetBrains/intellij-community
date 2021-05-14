// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class OpenLogAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    e.getPresentation().setEnabled(project != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }

    File log = new File(PathManager.getLogPath(), "idea.log");
    VirtualFile file = VfsUtil.findFileByIoFile(log, true);
    if (file != null) {
      VfsUtil.markDirtyAndRefresh(true, false, false, file);
      FileEditorManager.getInstance(project).openFile(file, true);
    }
    else {
      Notifications.Bus.notify(new Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID,
                                                "Couldn't find idea.log",
                                                "",
                                                NotificationType.INFORMATION));
    }
  }
}
