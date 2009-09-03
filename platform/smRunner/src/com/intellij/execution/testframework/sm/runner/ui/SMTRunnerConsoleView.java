package com.intellij.execution.testframework.sm.runner.ui;

import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.TestFrameworkRunningModel;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.testframework.ui.PrintableTestProxy;
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

  public SMTRunnerConsoleView(final TestConsoleProperties consoleProperties, final RunnerSettings runnerSettings,
                              final ConfigurationPerRunnerSettings configurationPerRunnerSettings,
                              @Nullable final String splitterProperty) {
    super(consoleProperties);
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

      public void onTestingFinished(TestResultsViewer sender) {
        // Do nothing
      }

      public void onSelected(@Nullable final PrintableTestProxy selectedTestProxy,
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
    getPrinter().setCollectOutput(false);
  }
}
