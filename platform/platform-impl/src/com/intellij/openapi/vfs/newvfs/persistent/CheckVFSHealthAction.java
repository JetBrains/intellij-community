// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.vfs.newvfs.persistent;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

final class CheckVFSHealthAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      VFSHealthChecker checker = new VFSHealthChecker(FSRecords.implOrFail(), FSRecords.LOG);
      VFSHealthChecker.VFSHealthCheckReport report = checker.checkHealth();
      FSRecords.LOG.info("VFS health check report: " + report );

      //run an old version still -- to compare:
      FSRecords.checkSanity();
    });
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}