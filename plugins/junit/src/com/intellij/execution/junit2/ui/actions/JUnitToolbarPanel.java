package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.ui.TestsUIUtil;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.execution.Location;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NonNls;

public class JUnitToolbarPanel extends ToolbarPanel {
  @NonNls protected static final String TEST_SUITE_CLASS_NAME = "junit.framework.TestSuite";
  private RerunFailedTestsAction myRerunFailedTestsAction;

  public JUnitToolbarPanel(final TestConsoleProperties properties,
                      final RunnerSettings runnerSettings,
                      final ConfigurationPerRunnerSettings configurationSettings) {
    super(properties, runnerSettings, configurationSettings);
  }

  protected void appendAdditionalActions(final DefaultActionGroup actionGroup, final TestConsoleProperties properties, final RunnerSettings runnerSettings,
                                         final ConfigurationPerRunnerSettings configurationSettings) {
    myRerunFailedTestsAction = new RerunFailedTestsAction((JUnitConsoleProperties)properties, runnerSettings, configurationSettings);
    actionGroup.add(myRerunFailedTestsAction);
  }


  public void setModel(final TestFrameworkRunningModel model) {
    super.setModel(model);
    final JUnitRunningModel jUnitModel = (JUnitRunningModel)model;
    myRerunFailedTestsAction.setModel(jUnitModel);
    JUnitActions.installAutoscrollToFirstDefect(jUnitModel);
    RunningTestTracker.install(jUnitModel);
    jUnitModel.addListener(new LvcsLabeler(jUnitModel));
    jUnitModel.addListener(new JUnitAdapter() {
      public void onTestSelected(final TestProxy test) {
        if (test == null) return;
        final Project project = jUnitModel.getProject();
        if (!ScrollToTestSourceAction.isScrollEnabled(model)) return;
        final Location location = test.getInfo().getLocation(project);
        if (location != null) {
          final PsiClass aClass = PsiTreeUtil.getParentOfType(location.getPsiElement(), PsiClass.class, false);
          if (aClass != null && JUnitToolbarPanel.TEST_SUITE_CLASS_NAME.equals(aClass.getQualifiedName())) return;
        }
        final Navigatable descriptor = TestsUIUtil.getOpenFileDescriptor(test, model);
        if (descriptor != null && descriptor.canNavigate()) {
          descriptor.navigate(false);
        }
      }
    });
  }
}
