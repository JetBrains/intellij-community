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
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ShowHideRecycledAction extends ToggleAction {

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(CommonDataKeys.PROJECT);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null);
    if (project != null) {
      presentation.setText(ShelveChangesManager.getInstance(project).isShowRecycled() ?
                           "Hide Already Unshelved" : "Show Already Unshelved");
      presentation.setIcon(ShelvedChangesViewManager.SHELF_CONTEXT_MENU.equals(e.getPlace()) ? null : AllIcons.Nodes.DisabledPointcut);
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    final ShelveChangesManager manager = ShelveChangesManager.getInstance(project);
    return manager.isShowRecycled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    ShelveChangesManager.getInstance(project).setShowRecycled(state);
  }
}
