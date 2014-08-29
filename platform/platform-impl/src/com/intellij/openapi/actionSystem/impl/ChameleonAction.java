/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class ChameleonAction extends AnAction {

  private final Map<ProjectType, AnAction> myActions = new HashMap<ProjectType, AnAction>();

  public ChameleonAction(@NotNull AnAction first, ProjectType projectType) {
    addAction(first, projectType);
  }

  public AnAction addAction(AnAction action, ProjectType projectType) {
    if (action instanceof ActionStub) {
      String type = ((ActionStub)action).getProjectType();
      action = ActionManagerImpl.convertStub((ActionStub)action);
      projectType = type == null ? null : new ProjectType(type);
    }
    return myActions.put(projectType, action);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    getAction(e).actionPerformed(e);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
  }

  private AnAction getAction(AnActionEvent e) {
    Project project = CommonDataKeys.PROJECT.getData(e.getDataContext());
    ProjectType projectType = ProjectTypeService.getProjectType(project);
    AnAction action = myActions.get(projectType);
    if (action == null) action = myActions.get(null);
    if (action == null) action = myActions.values().iterator().next();
    return action;
  }
}
