/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.junit2.ui.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitAdapter;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.TestsUIUtil;
import com.intellij.execution.testframework.ToolbarPanel;
import com.intellij.execution.testframework.actions.ScrollToTestSourceAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.config.ToggleBooleanProperty;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public class JUnitToolbarPanel extends ToolbarPanel {
  @NonNls protected static final String TEST_SUITE_CLASS_NAME = "junit.framework.TestSuite";

  public JUnitToolbarPanel(final TestConsoleProperties properties,
                           final ExecutionEnvironment environment,
                           final JComponent parentComponent) {
    super(properties, environment, parentComponent);
  }

  @Override
  protected void appendAdditionalActions(DefaultActionGroup actionGroup,
                                         TestConsoleProperties properties,
                                         ExecutionEnvironment environment, JComponent parent) {
    super.appendAdditionalActions(actionGroup, properties, environment, parent);
    actionGroup.addAction(new ToggleBooleanProperty(
      ExecutionBundle.message("junit.runing.info.include.non.started.in.rerun.failed.action.name"),
      null,
      null,
      properties, TestConsoleProperties.INCLUDE_NON_STARTED_IN_RERUN_FAILED)).setAsSecondary(true);
  }


  public void setModel(final TestFrameworkRunningModel model) {
    super.setModel(model);
    final JUnitRunningModel jUnitModel = (JUnitRunningModel)model;
    JUnitActions.installAutoscrollToFirstDefect(jUnitModel);
    RunningTestTracker.install(jUnitModel);
    jUnitModel.addListener(new LvcsLabeler(jUnitModel));
    jUnitModel.addListener(new JUnitAdapter() {
      public void onTestSelected(final TestProxy test) {
        if (test == null) return;
        final Project project = jUnitModel.getProject();
        if (!ScrollToTestSourceAction.isScrollEnabled(model)) return;
        final Location location = test.getInfo().getLocation(project, jUnitModel.getProperties().getScope());
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
