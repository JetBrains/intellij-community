// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.autotest;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.PreferredPlace;
import com.intellij.execution.runners.RunTab;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;


public class ToggleAutoTestAction extends ToggleAction {

  public ToggleAutoTestAction() {
    super(IdeBundle.messagePointer("action.ToggleAction.text.toggle.auto.test"),
          IdeBundle.messagePointer("action.ToggleAction.description.toggle.auto.test"),
          AllIcons.Actions.SwapPanels);
    getTemplatePresentation().putClientProperty(RunTab.PREFERRED_PLACE, PreferredPlace.MORE_GROUP);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunContentDescriptor descriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    return project != null && descriptor != null && getAutoTestManager(project).isAutoTestEnabled(descriptor);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    RunContentDescriptor descriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    ExecutionEnvironment environment = e.getData(ExecutionDataKeys.EXECUTION_ENVIRONMENT);
    if (project != null && descriptor != null && environment != null) {
      getAutoTestManager(project).setAutoTestEnabled(descriptor, environment, state);
    }
  }

  public boolean isDelayApplicable() {
    return true;
  }

  public AbstractAutoTestManager getAutoTestManager(Project project) {
    return AutoTestManager.getInstance(project);
  }
}
