// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    CachedValueProfiler profiler = CachedValueProfiler.getInstance();
    if (profiler.isEnabled()) {
      Project project = e.getData(CommonDataKeys.PROJECT);
      if (project != null) {
        DumpCachedValueProfilerInfoAction.dumpResults(project);
      }
    }
    profiler.setEnabled(!profiler.isEnabled());
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    CachedValueProfiler profiler = CachedValueProfiler.getInstance();
    e.getPresentation().setText(profiler.isEnabled() ? "Finish cached value profiling" : "Start cached value profiling");
  }
}
