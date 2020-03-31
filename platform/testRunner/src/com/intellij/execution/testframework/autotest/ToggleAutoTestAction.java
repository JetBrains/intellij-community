package com.intellij.execution.testframework.autotest;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ToggleAutoTestAction extends ToggleAction {

  public ToggleAutoTestAction() {
    super(IdeBundle.messagePointer("action.ToggleAction.text.toggle.auto.test"),
          IdeBundle.messagePointer("action.ToggleAction.description.toggle.auto.test"),
          AllIcons.Actions.SwapPanels);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    RunContentDescriptor descriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    return project != null && descriptor != null && getAutoTestManager(project).isAutoTestEnabled(descriptor);
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    RunContentDescriptor descriptor = e.getData(LangDataKeys.RUN_CONTENT_DESCRIPTOR);
    ExecutionEnvironment environment = e.getData(LangDataKeys.EXECUTION_ENVIRONMENT);
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
