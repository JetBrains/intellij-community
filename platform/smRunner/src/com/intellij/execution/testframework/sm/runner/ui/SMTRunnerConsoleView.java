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
package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.TestResultsPanel;
import com.intellij.openapi.application.ModalityState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author: Roman Chernyatchik
 */
public class SMTRunnerConsoleView extends BaseTestsOutputConsoleView {
  private SMTestRunnerResultsForm myResultsViewer;
  private final RunnerSettings myRunnerSettings;
  private final ConfigurationPerRunnerSettings myConfigurationPerRunnerSettings;
  @Nullable private final String mySplitterProperty;

  public SMTRunnerConsoleView(final TestConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationPerRunnerSettings) {
    this(consoleProperties, runnerSettings, configurationPerRunnerSettings, null);
  }

  /**
   *
   * @param consoleProperties
   * @param runnerSettings
   * @param configurationPerRunnerSettings
   * @param splitterProperty Key to store(project level) latest value of testTree/consoleTab splitter. E.g. "RSpec.Splitter.Proportion"
   */
  public SMTRunnerConsoleView(final TestConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationPerRunnerSettings,
                              @Nullable final String splitterProperty) {
    super(consoleProperties, null);
    myRunnerSettings = runnerSettings;
    myConfigurationPerRunnerSettings = configurationPerRunnerSettings;
    mySplitterProperty = splitterProperty;
  }

  protected TestResultsPanel createTestResultsPanel() {
    // Results View
    myResultsViewer = new SMTestRunnerResultsForm(myProperties.getConfiguration(),
                                                  getConsole().getComponent(),
                                                  getConsole().createConsoleActions(),
                                                  myProperties,
                                                  myRunnerSettings, myConfigurationPerRunnerSettings,
                                                  mySplitterProperty);
    return myResultsViewer;
  }

  @Override
  public void initUI() {
    super.initUI();

    // Console
    myResultsViewer.addEventsListener(new TestResultsViewer.EventsListener() {
      public void onTestNodeAdded(TestResultsViewer sender, SMTestProxy test) {
        // Do nothing
      }

      public void onTestingStarted(TestResultsViewer sender) {
        // Do nothing
      }

      public void onTestingFinished(TestResultsViewer sender) {
        // Do nothing
      }

      public void onSelected(@Nullable final SMTestProxy selectedTestProxy,
                             @NotNull final TestResultsViewer viewer,
                             @NotNull final TestFrameworkRunningModel model) {
        if (selectedTestProxy == null) {
          return;
        }

        // print selected content
        SMRunnerUtil.runInEventDispatchThread(new Runnable() {
          public void run() {
            getPrinter().updateOnTestSelected(selectedTestProxy);
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  public SMTestRunnerResultsForm getResultsViewer() {
    return myResultsViewer;
  }

  public void attachToProcess(final ProcessHandler processHandler) {
  }
}
