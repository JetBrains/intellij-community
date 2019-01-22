// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.statistics;

import com.intellij.diff.tools.util.DiffDataKeys;
import com.intellij.ide.ApplicationInitializedListener;
import com.intellij.ide.actions.BackAction;
import com.intellij.ide.actions.ForwardAction;
import com.intellij.internal.statistic.eventLog.FeatureUsageGroup;
import com.intellij.internal.statistic.eventLog.FeatureUsageLogger;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vcs.changes.ui.ChangesListView;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.util.ObjectUtils;
import com.intellij.vcs.log.VcsLogDataKeys;
import org.jetbrains.annotations.NotNull;

public class VcsBackForwardUsageTriggerCollector {

  private static final FeatureUsageGroup GROUP = new FeatureUsageGroup("statistics.vcs.back.forward.trigger",1);

  public static class Trigger implements ApplicationInitializedListener {
    @Override
    public void componentsInitialized() {
      ApplicationManager.getApplication().getMessageBus().connect().subscribe(AnActionListener.TOPIC, new AnActionListener() {
        @Override
        public void beforeActionPerformed(@NotNull AnAction action, @NotNull DataContext dataContext, @NotNull AnActionEvent e) {
          if (action instanceof BackAction ||
              action instanceof ForwardAction) {
            FeatureUsageLogger.INSTANCE.log(GROUP, getContextName(e));
          }
        }
      });
    }
  }

  @NotNull
  private static String getContextName(AnActionEvent e) {
    ToolWindowImpl toolWindow = ObjectUtils.tryCast(e.getData(PlatformDataKeys.TOOL_WINDOW), ToolWindowImpl.class);

    if (e.getData(VcsLogDataKeys.VCS_LOG) != null) return "vcs.log";
    if (e.getData(ChangesListView.DATA_KEY) != null) return "local.changes";
    if (toolWindow != null && toolWindow.getId().equals("Project")) return "project.view";
    if (e.getData(DiffDataKeys.DIFF_VIEWER) != null) return "diff.viewer";
    if (e.getData(CommonDataKeys.EDITOR) != null) return "editor";
    return "unknown";
  }
}
