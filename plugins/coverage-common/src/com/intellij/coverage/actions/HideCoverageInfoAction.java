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

import com.intellij.coverage.CoverageDataManager;
import com.intellij.coverage.CoverageSuitesBundle;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

public class HideCoverageInfoAction extends AnAction {
  public HideCoverageInfoAction() {
    super("&Hide Coverage Data", "Hide coverage data", AllIcons.Actions.Cancel);
  }

  public void actionPerformed(final AnActionEvent e) {
    CoverageDataManager.getInstance(e.getData(CommonDataKeys.PROJECT)).chooseSuitesBundle(null);
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    presentation.setEnabled(false);
    presentation.setVisible(e.isFromActionToolbar());
    final Project project = e.getProject();
    if (project != null) {
      final CoverageSuitesBundle suitesBundle = CoverageDataManager.getInstance(project).getCurrentSuitesBundle();
      presentation.setEnabled(suitesBundle != null);
      presentation.setVisible(suitesBundle != null);
    }
  }
}
