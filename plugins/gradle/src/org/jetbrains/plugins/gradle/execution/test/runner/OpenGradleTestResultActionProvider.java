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
package org.jetbrains.plugins.gradle.execution.test.runner;

import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.ToggleModelAction;
import com.intellij.execution.testframework.ToggleModelActionProvider;
import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.BooleanProperty;
import icons.GradleIcons;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;


/**
 * @author Vladislav.Soroka
 * @since 2/26/14
 */
public class OpenGradleTestResultActionProvider implements ToggleModelActionProvider {
  public static final BooleanProperty OPEN_GRADLE_REPORT = new BooleanProperty("openGradleReport", false);

  public ToggleModelAction createToggleModelAction(TestConsoleProperties properties) {
    return new MyToggleModelAction(properties);
  }

  private static class MyToggleModelAction extends ToggleModelAction {
    @Nullable
    private ProjectSystemId mySystemId;

    public MyToggleModelAction(TestConsoleProperties properties) {
      super(GradleBundle.message("gradle.test.runner.ui.tests.actions.open.gradle.report.text"),
            GradleBundle.message("gradle.test.runner.ui.tests.actions.open.gradle.report.desc"),
                                 GradleIcons.GradleNavigate, properties, OPEN_GRADLE_REPORT);
    }

    @Override
    public void setModel(TestFrameworkRunningModel model) {
      final RunProfile runConfiguration = model.getProperties().getConfiguration();
      if(runConfiguration instanceof ExternalSystemRunConfiguration) {
        mySystemId = ((ExternalSystemRunConfiguration)runConfiguration).getSettings().getExternalSystemId();
      }
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      final String reportFilePath = getReportFilePath();
      if (reportFilePath != null) {
        BrowserUtil.browse(reportFilePath);
      }
    }

    @Override
    protected boolean isEnabled() {
      final String reportFilePath = getReportFilePath();
      return reportFilePath != null;
    }

    @Override
    protected boolean isVisible() {
      return GradleConstants.SYSTEM_ID.equals(mySystemId);
    }

    @Nullable
    private String getReportFilePath() {
      final AbstractProperty.AbstractPropertyContainer properties = getProperties();
      if (properties instanceof GradleConsoleProperties) {
        GradleConsoleProperties gradleConsoleProperties = (GradleConsoleProperties)properties;
        final File testReport = gradleConsoleProperties.getGradleTestReport();
        if (testReport != null && testReport.isFile()) return testReport.getPath();
      }
      return null;
    }
  }
}
