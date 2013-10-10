package com.intellij.execution.testframework.autotest;

import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;

/**
 * @author yole
 */
public class ToggleAutoTestAction extends ToggleAction {
  public ToggleAutoTestAction(ExecutionEnvironment environment) {
    super("Toggle auto-test", "Toggle automatic rerun of tests on code changes",
          AllIcons.Actions.SwapPanels);
    environment.putUserData(AutoTestManager.AUTOTESTABLE, true);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    RunContentDescriptor descriptor = e.getData(RunContentManager.RUN_CONTENT_DESCRIPTOR);
    return !(project == null || descriptor == null) && AutoTestManager.getInstance(project).isAutoTestEnabled(descriptor);
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    RunContentDescriptor descriptor = e.getData(RunContentManager.RUN_CONTENT_DESCRIPTOR);
    if (project != null && descriptor != null) {
      AutoTestManager.getInstance(project).setAutoTestEnabled(descriptor, state);
    }
  }
}
