// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.coverage.CoverageBundle;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.ui.components.labels.LinkLabel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

public class HideCoverageInfoAction extends IconWithTextAction {
  public HideCoverageInfoAction() {
    super(CoverageBundle.messagePointer("coverage.hide.coverage.action.name"),
          CoverageBundle.messagePointer("coverage.hide.coverage.action.description"), null);
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    CoverageDataManager.getInstance(Objects.requireNonNull(e.getData(CommonDataKeys.PROJECT))).chooseSuitesBundle(null);
  }

  @NotNull
  @Override
  public JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
    return new LinkLabel(presentation.getText(), null) {
      @Override
      public void doClick() {
        DataContext dataContext = DataManager.getInstance().getDataContext(this);
        Project project = CommonDataKeys.PROJECT.getData(dataContext);
        CoverageDataManager.getInstance(project).chooseSuitesBundle(null);
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
      presentation.setEnabledAndVisible(CoverageDataManager.getInstance(project).getCurrentSuitesBundle() != null);
    }
  }
}
