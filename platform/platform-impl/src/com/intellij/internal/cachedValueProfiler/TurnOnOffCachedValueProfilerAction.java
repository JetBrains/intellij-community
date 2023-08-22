// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.cachedValueProfiler;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProfiler;
import org.jetbrains.annotations.NotNull;

final class TurnOnOffCachedValueProfilerAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    CachedValueProfilerDumpHelper.toggleProfiling(project);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    boolean enabled = project != null;
    boolean running = CachedValueProfiler.isProfiling();
    e.getPresentation().setEnabledAndVisible(enabled);
    e.getPresentation().setText(running ? "Stop Cached Value Profiling" : "Start Cached Value Profiling");
  }
}
