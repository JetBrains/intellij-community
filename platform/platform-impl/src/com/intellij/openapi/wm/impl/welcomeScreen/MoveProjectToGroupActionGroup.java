// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public final class MoveProjectToGroupActionGroup extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(@NotNull AnActionEvent e) {
    removeAll();
    List<ProjectGroup> groups = new ArrayList<>(RecentProjectsManager.getInstance().getGroups());
    groups.sort((o1, o2) -> StringUtil.naturalCompare(o1.getName(), o2.getName()));
    for (ProjectGroup group : groups) {
      if (group.isTutorials()) {
        continue;
      }
      add(new MoveProjectToGroupAction(group));
    }
    if (groups.size() > 0) {
      add(Separator.getInstance());
      add(new RemoveSelectedProjectsFromGroupsAction());
    }
  }
}
