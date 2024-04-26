// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

public class HideCoverageInfoAction extends IconWithTextAction {
  @Nullable private final CoverageSuitesBundle myCoverageSuite;

  public HideCoverageInfoAction() {
    this(null);
  }

  public HideCoverageInfoAction(@Nullable CoverageSuitesBundle bundle) {
    super(CoverageBundle.messagePointer("coverage.hide.coverage.action.name"),
          CoverageBundle.messagePointer("coverage.hide.coverage.action.description"), null);
    myCoverageSuite = bundle;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    Project project = Objects.requireNonNull(e.getData(CommonDataKeys.PROJECT));
    doAction(project);
  }

  private void doAction(Project project) {
    CoverageDataManager manager = CoverageDataManager.getInstance(project);
    if (myCoverageSuite == null) {
      for (CoverageSuitesBundle bundle : manager.activeSuites()) {
        manager.closeSuitesBundle(bundle);
      }
    }
    else {
      manager.closeSuitesBundle(myCoverageSuite);
    }
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new LinkLabel(presentation.getText(), null) {
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
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(e.isFromActionToolbar());
    final Project project = e.getProject();
    if (project != null) {
      Collection<CoverageSuitesBundle> activeSuites = CoverageDataManager.getInstance(project).activeSuites();
      boolean enabled = myCoverageSuite == null ? !activeSuites.isEmpty() : activeSuites.contains(myCoverageSuite);
      presentation.setEnabledAndVisible(enabled);
    }
  }
}
