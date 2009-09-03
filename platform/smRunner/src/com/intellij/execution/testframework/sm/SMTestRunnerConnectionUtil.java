package com.intellij.execution.testframework.sm;

import com.intellij.execution.ExecutionException;
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
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.sm.runner.ui.statistics.StatisticsPanel;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerConnectionUtil {
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
   * @param processHandler Process handler
   * @param consoleProperties Console properties for test console actions
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this Exception will
   * be catched and shown in error message box
   */
  public static BaseTestsOutputConsoleView attachRunner(@NotNull final ProcessHandler processHandler,
                                                        final TestConsoleProperties consoleProperties,
                                                        final RunnerSettings runnerSettings,
                                                        final ConfigurationPerRunnerSettings configurationSettings,
                                                        final String splitterPropertyName) throws ExecutionException {

    // Console
    final SMTRunnerConsoleView testRunnerConsole = new SMTRunnerConsoleView(consoleProperties, runnerSettings, configurationSettings, splitterPropertyName);
    testRunnerConsole.initUI();
    final SMTestRunnerResultsForm resultsViewer = testRunnerConsole.getResultsViewer();

    // attach listeners
    attachEventsProcessors(consoleProperties, resultsViewer, resultsViewer.getStatisticsPane(), processHandler);
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
   * @param processHandler Process handler
   * @param commandLineState  Command line state
   * @param config User run configuration settings
   * @param splitterPropertyName This property will be used for storing splitter position.
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this Exception will
   * be catched and shown in error message box
   */
  public static ConsoleView attachRunner(@NotNull final ProcessHandler processHandler,
                                         @NotNull final CommandLineState commandLineState,
                                         @NotNull final RuntimeConfiguration config,
                                         @Nullable final String splitterPropertyName) throws ExecutionException {
    final TestConsoleProperties consoleProperties = new SMTRunnerConsoleProperties(config);

    return attachRunner(processHandler, consoleProperties,
                        commandLineState.getRunnerSettings(), commandLineState.getConfigurationSettings(), splitterPropertyName);
  }

  private static ProcessHandler attachEventsProcessors(final TestConsoleProperties consoleProperties,
                                                       final SMTestRunnerResultsForm resultsViewer,
                                                       final StatisticsPanel statisticsPane,
                                                       final ProcessHandler processHandler)
      throws ExecutionException {

    //build messages consumer
    final OutputToGeneralTestEventsConverter outputConsumer = new OutputToGeneralTestEventsConverter();

    //events processor
    final GeneralToSMTRunnerEventsConvertor eventsProcessor = new GeneralToSMTRunnerEventsConvertor(resultsViewer.getTestsRootNode());

    //ui actions
    final SMTRunnerUIActionsHandler uiActionsHandler = new SMTRunnerUIActionsHandler(consoleProperties);

    // subscribe on events

    // subsrcibes event processor on output consumer events
    outputConsumer.setProcessor(eventsProcessor);
    // subsrcibes result viewer on event processor
    eventsProcessor.addEventsListener(resultsViewer);
    // subsrcibes test runner's actions on results viewer events
    resultsViewer.addEventsListener(uiActionsHandler);
    // subsrcibes statistics tab viewer on event processor
    eventsProcessor.addEventsListener(statisticsPane.createTestEventsListener());

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
