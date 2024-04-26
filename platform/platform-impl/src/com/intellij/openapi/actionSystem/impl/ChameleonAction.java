// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ActionStub;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectType;
import com.intellij.openapi.project.ProjectTypeService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public final class ChameleonAction extends AnAction {
  private final Map<ProjectType, AnAction> myActions = new HashMap<>();

  public ChameleonAction(@NotNull AnAction first, @Nullable ProjectType projectType) {
    addAction(first, projectType);
    copyFrom(myActions.values().iterator().next());
  }

  /**
   * @return true on success, false on action conflict
   */
  boolean addAction(@NotNull AnAction action, @Nullable ProjectType projectType) {
    if (action instanceof ActionStub actionStub) {
      action = ActionManagerImplKt.convertStub(actionStub);
      if (action == null) {
        return true;
      }

      projectType = actionStub.getProjectType();
    }

    if (myActions.containsKey(projectType)) {
      return false;
    }
    myActions.put(projectType, action);
    return true;
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

  @ApiStatus.Internal
  public Map<ProjectType, AnAction> getActions() {
    return myActions;
  }
}
