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

package com.intellij.execution.junit2.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.ui.model.JUnitRunningModel;
import com.intellij.execution.junit2.ui.model.TreeCollapser;
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;

import javax.swing.*;

public class JUnitTreeConsoleView extends BaseTestsOutputConsoleView {
  private ConsolePanel myConsolePanel;
  private final JUnitConsoleProperties myProperties;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationSettings;

  public JUnitTreeConsoleView(final JUnitConsoleProperties properties,
                              final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationSettings,
                              final AbstractTestProxy unboundOutputRoot) {
    super(properties, unboundOutputRoot);
    myProperties = properties;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
  }

  protected TestResultsPanel createTestResultsPanel() {
    myConsolePanel = new ConsolePanel(getConsole().getComponent(), getPrinter(), myProperties, myRunnerSettings, myConfigurationSettings,
                                      getConsole().createConsoleActions());
    return myConsolePanel;
  }

  public void attachToProcess(final ProcessHandler processHandler) {
    myConsolePanel.onProcessStarted(processHandler);
  }

  public void dispose() {
    super.dispose();
    myConsolePanel = null;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myConsolePanel.getTreeView();
  }

  public void attachToModel(JUnitRunningModel model) {
    myConsolePanel.getTreeView().attachToModel(model);
    model.attachToTree(myConsolePanel.getTreeView());
    myConsolePanel.setModel(model);
    model.onUIBuilt();
    new TreeCollapser().setModel(model);
  }
}
