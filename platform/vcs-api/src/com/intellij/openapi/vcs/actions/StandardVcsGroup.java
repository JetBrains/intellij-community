/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public abstract class StandardVcsGroup extends DefaultActionGroup implements DumbAware {
  public abstract AbstractVcs getVcs(Project project);

  public void update(AnActionEvent e) {
    Presentation presentation = e.getPresentation();

    Project project = e.getData(PlatformDataKeys.PROJECT);
    if (project != null) {
      final String vcsName = getVcsName(project);
      presentation.setVisible(vcsName != null &&
                              ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(vcsName));
    }
    else {
      presentation.setVisible(false);
    }
    presentation.setEnabled(presentation.isVisible());
  }

  @Nullable
  @NonNls
  public String getVcsName(Project project) {
    final AbstractVcs vcs = getVcs(project);
    // if the parent group was customized and then the plugin was disabled, we could have an action group with no VCS
    return vcs != null ? vcs.getName() : null;
  }
}
