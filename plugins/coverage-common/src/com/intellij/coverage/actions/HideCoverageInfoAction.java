// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.ui.components.labels.LinkLabel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

@ApiStatus.Internal
public class HideCoverageInfoAction extends IconWithTextAction {

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getData(CommonDataKeys.PROJECT));
    doAction(project);
  }

  private static void doAction(Project project) {
    CoverageDataManager manager = CoverageDataManager.getInstance(project);
    for (CoverageSuitesBundle bundle : manager.activeSuites()) {
      manager.closeSuitesBundle(bundle);
    }
  }

  @Override
  public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new LinkLabel<>(CoverageBundle.message("coverage.hide.coverage.link.name"), null) {
      @Override
      public void doClick() {
        DataContext dataContext = DataManager.getInstance().getDataContext(this);
        Project project = Objects.requireNonNull(CommonDataKeys.PROJECT.getData(dataContext));
        doAction(project);
        HintManagerImpl.getInstanceImpl().hideAllHints();
      }
    };
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final Project project = e.getProject();
    if (project == null) return;
    Collection<CoverageSuitesBundle> activeSuites = CoverageDataManager.getInstance(project).activeSuites();
    e.getPresentation().setEnabledAndVisible(!activeSuites.isEmpty());
  }
}
