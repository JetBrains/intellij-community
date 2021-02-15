// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.cachedValueProfiler;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.util.CachedValueProfiler;
import org.jetbrains.annotations.NotNull;

public class TurnOnOffCachedValueProfilerAction extends DumbAwareAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;
    CachedValueProfilerDumpHelper.toggleProfiling(project);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    boolean enabled = project != null;
    boolean running = CachedValueProfiler.isProfiling();
    e.getPresentation().setEnabledAndVisible(enabled);
    e.getPresentation().setText(running ? "Stop Cached Value Profiling" : "Start Cached Value Profiling");
  }
}
