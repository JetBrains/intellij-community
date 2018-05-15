/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage.actions;

import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.coverage.CoverageDataManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.ui.components.labels.LinkLabel;

import javax.swing.*;

public class HideCoverageInfoAction extends IconWithTextAction {
  public HideCoverageInfoAction() {
    super("Hide coverage", "Hide coverage data", null);
  }

  public void actionPerformed(final AnActionEvent e) {
    CoverageDataManager.getInstance(e.getData(CommonDataKeys.PROJECT)).chooseSuitesBundle(null);
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
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
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(e.isFromActionToolbar());
    final Project project = e.getProject();
    if (project != null) {
      presentation.setEnabledAndVisible(CoverageDataManager.getInstance(project).getCurrentSuitesBundle() != null);
    }
  }
}
