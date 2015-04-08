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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;

/**
 * @author Konstantin Bulenkov
 */
public class CreateNewProjectGroupAction extends RecentProjectsWelcomeScreenActionBase {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final InputValidator validator = new InputValidator() {
      @Override
      public boolean checkInput(String inputString) {
        inputString = inputString.trim();
        return getGroup(inputString) == null;
      }

      @Override
      public boolean canClose(String inputString) {
        return true;
      }
    };
    final String newGroup = Messages.showInputDialog((Project)null, "Project group name", "Create New Project Group", null, null, validator);
    if (newGroup != null) {
      final ProjectGroup group = new ProjectGroup(newGroup);
      RecentProjectsManager.getInstance().addGroup(group);
      rebuildRecentProjectsList(e);
    }
  }

  private static ProjectGroup getGroup(String name) {
    for (ProjectGroup group : RecentProjectsManager.getInstance().getGroups()) {
      if (group.getName().equals(name)) {
        return group;
      }
    }
    return null;
  }
}
