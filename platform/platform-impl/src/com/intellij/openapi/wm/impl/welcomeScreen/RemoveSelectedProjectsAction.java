// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroupActionGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.ReopenProjectAction;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.ui.ScrollingUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
final class RemoveSelectedProjectsAction extends RecentProjectsWelcomeScreenActionBase {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    RecentProjectsManager manager = RecentProjectsManager.getInstance();
    for (AnAction action : getSelectedElements(e)) {
      if (action instanceof ReopenProjectAction) {
        manager.removePath(((ReopenProjectAction)action).getProjectPath());
      }
      else if (action instanceof ProjectGroupActionGroup) {
        manager.removeGroup(((ProjectGroupActionGroup)action).getGroup());
      }

      rebuildRecentProjectsList(e);

      JList list = getList(e);
      if (list != null) {
        ScrollingUtil.ensureSelectionExists(list);
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!getSelectedElements(e).isEmpty());
  }
}
