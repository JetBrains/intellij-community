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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ShowHideRecycledAction extends ToggleAction implements DumbAware {

  @Override
  public void update(@NotNull final AnActionEvent e) {
    super.update(e);
    final Project project = getEventProject(e);
    final Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(project != null);
    if (project != null) {
      final boolean fromContextMenu = ShelvedChangesViewManager.SHELF_CONTEXT_MENU.equals(e.getPlace());
      presentation.setText(ShelveChangesManager.getInstance(project).isShowRecycled() && !fromContextMenu ?
                           "Hide Already Unshelved" : "Show Already Unshelved");
      presentation.setIcon(fromContextMenu ? null : AllIcons.Nodes.DisabledPointcut);
    }
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    final Project project = getEventProject(e);
    return project != null && ShelveChangesManager.getInstance(project).isShowRecycled();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    final Project project = getEventProject(e);
    if (project != null) {
      ShelveChangesManager.getInstance(project).setShowRecycled(state);
    }
  }
}
