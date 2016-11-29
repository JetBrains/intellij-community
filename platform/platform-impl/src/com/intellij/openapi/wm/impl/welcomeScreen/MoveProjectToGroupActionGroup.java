/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.text.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class MoveProjectToGroupActionGroup extends DefaultActionGroup implements DumbAware {
  @Override
  public void update(AnActionEvent e) {
    removeAll();
    final List<ProjectGroup> groups = new ArrayList<>(RecentProjectsManager.getInstance().getGroups());
    Collections.sort(groups, (o1, o2) -> StringUtil.naturalCompare(o1.getName(), o2.getName()));
    for (ProjectGroup group : groups) {
      add(new MoveProjectToGroupAction(group));
    }
    if (groups.size() > 0) {
      add(Separator.getInstance());
      add(new RemoveSelectedProjectsFromGroupsAction());
    }
  }
}
