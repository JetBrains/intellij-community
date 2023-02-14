/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionStub;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ChameleonAction extends AnAction {

  private final Map<ProjectType, AnAction> myActions = new HashMap<>();

  public ChameleonAction(@NotNull AnAction first, @Nullable ProjectType projectType) {
    addAction(first, projectType);
    copyFrom(myActions.values().iterator().next());
  }

  @Nullable ChameleonAction addAction(@NotNull AnAction action, @Nullable ProjectType projectType) {
    if (action instanceof ActionStub actionStub) {
      action = ActionManagerImpl.convertStub(actionStub);

      if (action == null) return this;
      projectType = actionStub.getProjectType();
    }
    return myActions.put(projectType, action) == null ? this : null;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    AnAction action = getAction(e);
    assert action != null;
    action.actionPerformed(e);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    AnAction action = getAction(e);
    boolean visible = action != null;
    e.getPresentation().setVisible(visible);
    if (visible) {
      action.update(e);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    AnAction action = myActions.get(null);
    if (action == null) action = myActions.values().iterator().next();
    return action == null ? ActionUpdateThread.BGT : action.getActionUpdateThread();
  }

  private @Nullable AnAction getAction(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    ProjectType projectType = ProjectTypeService.getProjectType(project);
    AnAction action = myActions.get(projectType);
    return action != null ? action : myActions.get(null);
  }

  @TestOnly
  public Map<ProjectType, AnAction> getActions() {
    return myActions;
  }
}
