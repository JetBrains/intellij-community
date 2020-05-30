// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class MoveProjectToGroupAction extends RecentProjectsWelcomeScreenActionBase {
  private final ProjectGroup myGroup;

  public MoveProjectToGroupAction(ProjectGroup group) {
    myGroup = group;
    getTemplatePresentation().setText(group.getName());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    for (AnAction element : getSelectedElements(e)) {
      if (!(element instanceof ReopenProjectAction)) {
        continue;
      }

      String path = ((ReopenProjectAction)element).getProjectPath();
      for (ProjectGroup group : RecentProjectsManager.getInstance().getGroups()) {
        group.removeProject(path);
        myGroup.addProject(path);
      }
    }
    rebuildRecentProjectsList(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!hasGroupSelected(e));
  }
}
