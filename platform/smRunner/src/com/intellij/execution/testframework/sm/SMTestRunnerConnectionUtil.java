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
package com.intellij.execution.testframework.sm;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.configurations.RuntimeConfiguration;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.GeneralToSMTRunnerEventsConvertor;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerNotificationsHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.sm.runner.ui.statistics.StatisticsPanel;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerConnectionUtil {
  private static final String TEST_RUNNER_DEBUG_MODE_PROPERTY = "idea.smrunner.debug";

  private SMTestRunnerConnectionUtil() {
    // Do nothing. Utility class.
  }

  /**
   * Creates Test Runner console component with test tree, console, statistics tabs
   * and attaches it to given Process handler.
   *
   * You can use this method in run configuration's CommandLineState. You should
   * just override "execute" method of your custom command line state and return
   * test runner's console.
   *
   * NB: For debug purposes please enable "debug mode". In this mode test runner will also validate
   * consistency of test events communication protocol and throw assertion errors. To enable debug mode
   * please set system property idea.smrunner.debug=true
   *
   * @param testFrameworkName Is used to store(project level) latest value of testTree/consoleTab splitter and other settings
   * and also will be mentioned in debug diagnostics
   * @param processHandler Process handler
   * @param consoleProperties Console properties for test console actions
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this Exception will
   * be caught and shown in error message box
   */
  public static BaseTestsOutputConsoleView attachRunner(@NotNull final String testFrameworkName,
                                                        @NotNull final ProcessHandler processHandler,
                                                        @NotNull final TestConsoleProperties consoleProperties,
                                                        final RunnerSettings runnerSettings,
                                                        final ConfigurationPerRunnerSettings configurationSettings
  ) throws ExecutionException {

    // Console
    final String splitterPropertyName = testFrameworkName + ".Splitter.Proportion";
    final SMTRunnerConsoleView testRunnerConsole = new SMTRunnerConsoleView(consoleProperties, runnerSettings, configurationSettings, splitterPropertyName);
    testRunnerConsole.initUI();
    final SMTestRunnerResultsForm resultsViewer = testRunnerConsole.getResultsViewer();

    // attach listeners
    attachEventsProcessors(consoleProperties, resultsViewer,
                           resultsViewer.getStatisticsPane(),
                           processHandler, testFrameworkName);
    testRunnerConsole.attachToProcess(processHandler);

    return testRunnerConsole;
  }

  /**
   * Creates Test Runner console component with test tree, console, statistics tabs
   * and attaches it to given Process handler.
   *
   * You can use this method in run configuration's CommandLineState. You should
   * just override "execute" method of your custom command line state and return
   * test runner's console.
   *
   * E.g:
   * <code>
   * public class MyCommandLineState extends CommandLineState {
   *
   *   // ...
   *
   *   @Override
   *   public ExecutionResult execute(@NotNull final Executor executor,
   *                                  @NotNull final ProgramRunner runner) throws ExecutionException {
   *
   *     final ProcessHandler processHandler = startProcess();
   *     final AbstractRubyRunConfiguration runConfiguration = getConfig();
   *     final Project project = runConfiguration.getProject();
   *
   *     final ConsoleView console =
   *       SMTestRunnerConnectionUtil.attachRunner(project, processHandler, this, runConfiguration,
   *                                               "MY_TESTRUNNER_SPLITTER_SETTINGS");
   *
   *     return new DefaultExecutionResult(console, processHandler,
   *                                      createActions(console, processHandler));
   *    }
   * }
   * </code>
   *
   *
   * NB: For debug purposes please enable "debug mode". In this mode test runner will also validate
   * consistency of test events communication protocol and throw assertion errors. To enable debug mode
   * please set system property idea.smrunner.debug=true
   *
   * @param testFrameworkName Is used to store(project level) latest value of testTree/consoleTab splitter and other settings
   * @param processHandler Process handler
   * @param commandLineState  Command line state
   * @param config User run configuration settings
   * @param executor Executor
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this Exception will
   * be caught and shown in error message box
   */
  public static ConsoleView attachRunner(@NotNull final String testFrameworkName, @NotNull final ProcessHandler processHandler,
                                         @NotNull final CommandLineState commandLineState,
                                         @NotNull final RuntimeConfiguration config,
                                         @NotNull final Executor executor
  ) throws ExecutionException {
    // final String testFrameworkName
    final TestConsoleProperties consoleProperties = new SMTRunnerConsoleProperties(config, testFrameworkName, executor);

    return attachRunner(testFrameworkName, processHandler, consoleProperties,
                        commandLineState.getRunnerSettings(),
                        commandLineState.getConfigurationSettings());
  }

  /**
   * In debug mode SM Runner will check events consistency. All errors will be reported using IDEA errors logger.
   * This mode must be disabled in production. The most widespread false positives were detected when you debug tests.
   * In such cases Test Framework may fire events several times, etc.
   * @return true if in debug mode, otherwise false.
   */
  public static boolean isInDebugMode() {
    return Boolean.valueOf(System.getProperty(TEST_RUNNER_DEBUG_MODE_PROPERTY));
  }

  private static ProcessHandler attachEventsProcessors(@NotNull final TestConsoleProperties consoleProperties,
                                                       final SMTestRunnerResultsForm resultsViewer,
                                                       final StatisticsPanel statisticsPane,
                                                       final ProcessHandler processHandler,
                                                       @NotNull final String testFrameworkName)
      throws ExecutionException {

    //build messages consumer
    final OutputToGeneralTestEventsConverter outputConsumer = new OutputToGeneralTestEventsConverter(testFrameworkName);

    //events processor
    final GeneralToSMTRunnerEventsConvertor eventsProcessor = new GeneralToSMTRunnerEventsConvertor(resultsViewer.getTestsRootNode(),
                                                                                                    testFrameworkName);

    // ui actions
    final SMTRunnerUIActionsHandler uiActionsHandler = new SMTRunnerUIActionsHandler(consoleProperties);
    // notifications
    final SMTRunnerNotificationsHandler notifierHandler = new SMTRunnerNotificationsHandler(consoleProperties);

    // subscribe on events

    // subscribes event processor on output consumer events
    outputConsumer.setProcessor(eventsProcessor);
    // subscribes result viewer on event processor
    eventsProcessor.addEventsListener(resultsViewer);
    // subscribes test runner's actions on results viewer events
    resultsViewer.addEventsListener(uiActionsHandler);
    // subscribes statistics tab viewer on event processor
    eventsProcessor.addEventsListener(statisticsPane.createTestEventsListener());
    // subscribes test runner's notification balloons on results viewer events
    eventsProcessor.addEventsListener(notifierHandler);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(final ProcessEvent event) {
        Disposer.dispose(eventsProcessor);
        Disposer.dispose(outputConsumer);
      }

      @Override
      public void startNotified(final ProcessEvent event) {
        eventsProcessor.onStartTesting();
      }

      @Override
      public void processWillTerminate(ProcessEvent event, boolean willBeDestroyed) {
        outputConsumer.flushBufferBeforeTerminating();
        eventsProcessor.onFinishTesting();
      }

      @Override
      public void onTextAvailable(final ProcessEvent event, final Key outputType) {
        outputConsumer.process(event.getText(), outputType);
      }
    });
    return processHandler;
  }

}
