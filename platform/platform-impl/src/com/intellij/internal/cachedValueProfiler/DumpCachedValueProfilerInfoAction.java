// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.intellij.CommonBundle;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

public class DumpCachedValueProfilerInfoAction extends DumbAwareAction {
  private static final NotificationGroup GROUP = new NotificationGroup("Cached value profiling", NotificationDisplayType.BALLOON, false);

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    dumpResults(project);
  }

  static void dumpResults(Project project) {
    try {
      File file = CachedValueProfilerDumper.dumpResults(new File(PathManager.getLogPath()));
      String url = FileUtil.getUrl(file);
      String message = CommonBundle.message("cached.value.snapshot.success", file, url, ShowFilePathAction.getFileManagerName());
      GROUP.createNotification("", message, NotificationType.INFORMATION, ShowFilePathAction.FILE_SELECTING_LISTENER).notify(project);
    }
    catch (IOException exception) {
      GROUP.createNotification(CommonBundle.message("cached.value.snapshot.error", exception.getMessage()),
                               NotificationType.ERROR).notify(project);
    }
  }
}
