/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.execution.Location;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.*;
import com.intellij.execution.testframework.sm.runner.ui.AttachToProcessListener;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public class SMTestRunnerConnectionUtil {
  private static final String TEST_RUNNER_DEBUG_MODE_PROPERTY = "idea.smrunner.debug";

  private SMTestRunnerConnectionUtil() { }

  /**
   * Creates Test Runner console component with test tree, console, statistics tabs
   * and attaches it to given Process handler.
   * <p/>
   * You can use this method in run configuration's CommandLineState. You should
   * just override "execute" method of your custom command line state and return
   * test runner's console.
   * <p/>
   * E.g: <pre>{@code
   * public class MyCommandLineState extends CommandLineState {
   *
   *   // ...
   *
   *   @Override
   *   public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner runner) throws ExecutionException {
   *     ProcessHandler processHandler = startProcess();
   *     RunConfiguration runConfiguration = getConfiguration();
   *     ExecutionEnvironment environment = getEnvironment();
   *     TestConsoleProperties properties = new SMTRunnerConsoleProperties(runConfiguration, "xUnit", executor)
   *     ConsoleView console = SMTestRunnerConnectionUtil.createAndAttachConsole("xUnit", processHandler, properties, environment);
   *     return new DefaultExecutionResult(console, processHandler, createActions(console, processHandler));
   *   }
   * }
   * }</pre>
   * <p/>
   * NB: For debug purposes please enable "debug mode". In this mode test runner will also validate
   * consistency of test events communication protocol and throw assertion errors. To enable debug mode
   * please set system property idea.smrunner.debug=true
   *
   * @param testFrameworkName Is used to store(project level) latest value of testTree/consoleTab splitter and other settings
   *                          and also will be mentioned in debug diagnostics
   * @param processHandler    Process handler
   * @param consoleProperties Console properties for test console actions
   * @return Console view
   * @throws ExecutionException If IDEA cannot execute process this exception will
   *                            be caught and shown in error message box
   */
  @NotNull
  public static BaseTestsOutputConsoleView createAndAttachConsole(@NotNull String testFrameworkName,
                                                                  @NotNull ProcessHandler processHandler,
                                                                  @NotNull TestConsoleProperties consoleProperties) throws ExecutionException {
    BaseTestsOutputConsoleView console = createConsole(testFrameworkName, consoleProperties);
    console.attachToProcess(processHandler);
    return console;
  }

  @NotNull
  public static BaseTestsOutputConsoleView createConsole(@NotNull String testFrameworkName,
                                                         @NotNull TestConsoleProperties consoleProperties) {
    String splitterPropertyName = getSplitterPropertyName(testFrameworkName);
    SMTRunnerConsoleView consoleView = new SMTRunnerConsoleView(consoleProperties, splitterPropertyName);
    initConsoleView(consoleView, testFrameworkName);
    return consoleView;
  }

  @NotNull
  public static String getSplitterPropertyName(@NotNull String testFrameworkName) {
    return testFrameworkName + ".Splitter.Proportion";
  }

  public static void initConsoleView(@NotNull final SMTRunnerConsoleView consoleView, @NotNull final String testFrameworkName) {
    consoleView.addAttachToProcessListener(new AttachToProcessListener() {
      @Override
      public void onAttachToProcess(@NotNull ProcessHandler processHandler) {
        TestConsoleProperties properties = consoleView.getProperties();

        TestProxyPrinterProvider printerProvider = null;
        if (properties instanceof SMTRunnerConsoleProperties) {
          TestProxyFilterProvider filterProvider = ((SMTRunnerConsoleProperties)properties).getFilterProvider();
          if (filterProvider != null) {
            printerProvider = new TestProxyPrinterProvider(consoleView, filterProvider);
          }
        }

        SMTestLocator testLocator = FileUrlProvider.INSTANCE;
        if (properties instanceof SMTRunnerConsoleProperties) {
          SMTestLocator customLocator = ((SMTRunnerConsoleProperties)properties).getTestLocator();
          if (customLocator != null) {
            testLocator = new CombinedTestLocator(customLocator);
          }
        }

        boolean idBasedTestTree = false;
        if (properties instanceof SMTRunnerConsoleProperties) {
          idBasedTestTree = ((SMTRunnerConsoleProperties)properties).isIdBasedTestTree();
        }

        SMTestRunnerResultsForm resultsForm = consoleView.getResultsViewer();
        attachEventsProcessors(properties,
                               resultsForm,
                               processHandler,
                               testFrameworkName,
                               testLocator,
                               idBasedTestTree,
                               printerProvider);
      }
    });
    consoleView.setHelpId("reference.runToolWindow.testResultsTab");
    consoleView.initUI();
  }

  /**
   * In debug mode SM Runner will check events consistency. All errors will be reported using IDEA errors logger.
   * This mode must be disabled in production. The most widespread false positives were detected when you debug tests.
   * In such cases Test Framework may fire events several times, etc.
   *
   * @return true if in debug mode, otherwise false.
   */
  public static boolean isInDebugMode() {
    return Boolean.valueOf(System.getProperty(TEST_RUNNER_DEBUG_MODE_PROPERTY));
  }

  private static void attachEventsProcessors(TestConsoleProperties consoleProperties,
                                             SMTestRunnerResultsForm resultsViewer,
                                             ProcessHandler processHandler,
                                             String testFrameworkName,
                                             @Nullable SMTestLocator locator,
                                             boolean idBasedTestTree,
                                             @Nullable TestProxyPrinterProvider printerProvider) {
    // build messages consumer
    final OutputToGeneralTestEventsConverter outputConsumer;
    if (consoleProperties instanceof SMCustomMessagesParsing) {
      outputConsumer = ((SMCustomMessagesParsing)consoleProperties).createTestEventsConverter(testFrameworkName, consoleProperties);
    }
    else {
      outputConsumer = new OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties);
    }

    // events processor
    final GeneralTestEventsProcessor eventsProcessor;
    if (idBasedTestTree) {
      eventsProcessor = new GeneralIdBasedToSMTRunnerEventsConvertor(consoleProperties.getProject(), resultsViewer.getTestsRootNode(), testFrameworkName);
    }
    else {
      eventsProcessor = new GeneralToSMTRunnerEventsConvertor(consoleProperties.getProject(), resultsViewer.getTestsRootNode(), testFrameworkName);
    }

    if (locator != null) {
      eventsProcessor.setLocator(locator);
    }

    if (printerProvider != null) {
      eventsProcessor.setPrinterProvider(printerProvider);
    }

    // UI actions
    final SMTRunnerUIActionsHandler uiActionsHandler = new SMTRunnerUIActionsHandler(consoleProperties);

    // subscribe to events

    // subscribes event processor on output consumer events
    outputConsumer.setProcessor(eventsProcessor);
    // subscribes result viewer on event processor
    eventsProcessor.addEventsListener(resultsViewer);
    // subscribes test runner's actions on results viewer events
    resultsViewer.addEventsListener(uiActionsHandler);

    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void processTerminated(@NotNull final ProcessEvent event) {
        outputConsumer.flushBufferBeforeTerminating();
        eventsProcessor.onFinishTesting();

        Disposer.dispose(eventsProcessor);
        Disposer.dispose(outputConsumer);
      }

      @Override
      public void startNotified(@NotNull final ProcessEvent event) {
        eventsProcessor.onStartTesting();
        outputConsumer.onStartTesting();
      }

      @Override
      public void onTextAvailable(@NotNull final ProcessEvent event, @NotNull final Key outputType) {
        outputConsumer.process(event.getText(), outputType);
      }
    });
  }

  private static class CombinedTestLocator implements SMTestLocator, DumbAware {
    private final SMTestLocator myLocator;

    public CombinedTestLocator(SMTestLocator locator) {
      myLocator = locator;
    }

    @NotNull
    @Override
    public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
        return FileUrlProvider.INSTANCE.getLocation(protocol, path, project, scope);
      }
      else if (!DumbService.isDumb(project) || DumbService.isDumbAware(myLocator) || Registry.is("dumb.aware.run.configurations")) {
        return myLocator.getLocation(protocol, path, project, scope);
      }
      else {
        return Collections.emptyList();
      }
    }
  }

  /** @deprecated use {@link #createConsole(String, TestConsoleProperties)} (to be removed in IDEA 17) */
  @SuppressWarnings({"unused", "deprecation"})
  public static BaseTestsOutputConsoleView createConsoleWithCustomLocator(@NotNull String testFrameworkName,
                                                                          @NotNull TestConsoleProperties consoleProperties,
                                                                          ExecutionEnvironment environment,
                                                                          @Nullable TestLocationProvider locator) {
    return createConsoleWithCustomLocator(testFrameworkName, consoleProperties, environment, locator, false, null);
  }

  /** @deprecated use {@link #createConsole(String, TestConsoleProperties)} (to be removed in IDEA 17) */
  @SuppressWarnings({"unused", "deprecation"})
  public static SMTRunnerConsoleView createConsoleWithCustomLocator(@NotNull String testFrameworkName,
                                                                    @NotNull TestConsoleProperties consoleProperties,
                                                                    ExecutionEnvironment environment,
                                                                    @Nullable TestLocationProvider locator,
                                                                    boolean idBasedTreeConstruction,
                                                                    @Nullable TestProxyFilterProvider filterProvider) {
    String splitterPropertyName = getSplitterPropertyName(testFrameworkName);
    SMTRunnerConsoleView consoleView = new SMTRunnerConsoleView(consoleProperties, splitterPropertyName);
    initConsoleView(consoleView, testFrameworkName, locator, idBasedTreeConstruction, filterProvider);
    return consoleView;
  }

  /** @deprecated use {@link #initConsoleView(SMTRunnerConsoleView, String)} (to be removed in IDEA 17) */
  @SuppressWarnings({"unused", "deprecation"})
  public static void initConsoleView(@NotNull final SMTRunnerConsoleView consoleView,
                                     @NotNull final String testFrameworkName,
                                     @Nullable final TestLocationProvider locator,
                                     final boolean idBasedTreeConstruction,
                                     @Nullable final TestProxyFilterProvider filterProvider) {
    consoleView.addAttachToProcessListener(new AttachToProcessListener() {
      @Override
      public void onAttachToProcess(@NotNull ProcessHandler processHandler) {
        TestConsoleProperties properties = consoleView.getProperties();

        SMTestLocator testLocator = new CompositeTestLocationProvider(locator);

        TestProxyPrinterProvider printerProvider = null;
        if (filterProvider != null) {
          printerProvider = new TestProxyPrinterProvider(consoleView, filterProvider);
        }

        SMTestRunnerResultsForm resultsForm = consoleView.getResultsViewer();
        attachEventsProcessors(properties,
                               resultsForm,
                               processHandler,
                               testFrameworkName,
                               testLocator,
                               idBasedTreeConstruction,
                               printerProvider);
      }
    });
    consoleView.setHelpId("reference.runToolWindow.testResultsTab");
    consoleView.initUI();
  }

  @SuppressWarnings("deprecation")
  private static class CompositeTestLocationProvider implements SMTestLocator {
    private final TestLocationProvider myPrimaryLocator;
    private final TestLocationProvider[] myLocators;

    private CompositeTestLocationProvider(@Nullable TestLocationProvider primaryLocator) {
      myPrimaryLocator = primaryLocator;
      myLocators = Extensions.getExtensions(TestLocationProvider.EP_NAME);
    }

    @NotNull
    @Override
    public List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      boolean isDumbMode = DumbService.isDumb(project);

      if (myPrimaryLocator != null && (!isDumbMode || myPrimaryLocator instanceof DumbAware)) {
        List<Location> locations = myPrimaryLocator.getLocation(protocol, path, project);
        if (!locations.isEmpty()) {
          return locations;
        }
      }

      if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
        List<Location> locations = FileUrlProvider.INSTANCE.getLocation(protocol, path, project, scope);
        if (!locations.isEmpty()) {
          return locations;
        }
      }

      for (TestLocationProvider provider : myLocators) {
        if (!isDumbMode || provider instanceof DumbAware) {
          List<Location> locations = provider.getLocation(protocol, path, project);
          if (!locations.isEmpty()) {
            return locations;
          }
        }
      }

      return Collections.emptyList();
    }
  }
}