// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessListener;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.*;
import com.intellij.execution.testframework.sm.runner.ui.AttachToProcessListener;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerUIActionsHandler;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.List;

/**
 * @author Roman Chernyatchik
 */
public final class SMTestRunnerConnectionUtil {
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
   *   public ExecutionResult execute(@NotNull Executor executor, @NotNull ProgramRunner<?> runner) throws ExecutionException {
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
   */
  public static @NotNull BaseTestsOutputConsoleView createAndAttachConsole(@NotNull String testFrameworkName,
                                                                  @NotNull ProcessHandler processHandler,
                                                                  @NotNull TestConsoleProperties consoleProperties) throws ExecutionException {
    BaseTestsOutputConsoleView console = createConsole(testFrameworkName, consoleProperties);
    console.attachToProcess(processHandler);
    return console;
  }

  public static @NotNull BaseTestsOutputConsoleView createConsole(@NotNull String testFrameworkName,
                                                                  @NotNull TestConsoleProperties consoleProperties) {
    String splitterPropertyName = getSplitterPropertyName(testFrameworkName);
    SMTRunnerConsoleView consoleView = new SMTRunnerConsoleView(consoleProperties, splitterPropertyName);
    initConsoleView(consoleView, testFrameworkName);
    return consoleView;
  }

  public static @NotNull SMTRunnerConsoleView createConsole(@NotNull SMTRunnerConsoleProperties consoleProperties) {
    return (SMTRunnerConsoleView)createConsole(consoleProperties.getTestFrameworkName(), consoleProperties);
  }

  public static @NotNull String getSplitterPropertyName(@NotNull String testFrameworkName) {
    return testFrameworkName + ".Splitter.Proportion";
  }

  public static void initConsoleView(final @NotNull SMTRunnerConsoleView consoleView, final @NotNull String testFrameworkName) {
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
        resultsForm.getTestsRootNode().setHandler(processHandler);
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
    return Boolean.parseBoolean(System.getProperty(TEST_RUNNER_DEBUG_MODE_PROPERTY));
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

    // UI actions
    SMTRunnerUIActionsHandler uiActionsHandler = new SMTRunnerUIActionsHandler(consoleProperties);
    // subscribes test runner's actions on results viewer events
    resultsViewer.addEventsListener(uiActionsHandler);

    outputConsumer.setTestingStartedHandler(() -> {
      // events processor
      GeneralTestEventsProcessor eventsProcessor;
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
      // subscribes result viewer on event processor
      eventsProcessor.addEventsListener(resultsViewer);

      // subscribes event processor on output consumer events
      outputConsumer.setProcessor(eventsProcessor);
    });

    outputConsumer.setupProcessor();
    processHandler.addProcessListener(new ProcessListener() {
      @Override
      public void startNotified(@NotNull ProcessEvent event) {
        outputConsumer.startTesting();
      }

      @Override
      public void processTerminated(final @NotNull ProcessEvent event) {
        outputConsumer.flushBufferOnProcessTermination(event.getExitCode());
        outputConsumer.finishTesting();
        Disposer.dispose(outputConsumer);
      }

      @Override
      public void onTextAvailable(final @NotNull ProcessEvent event, final @NotNull Key outputType) {
        outputConsumer.process(event.getText(), outputType);
      }
    });
  }

  private static class CombinedTestLocator implements SMTestLocator, DumbAware {
    private final SMTestLocator myLocator;

    CombinedTestLocator(SMTestLocator locator) {
      myLocator = locator;
    }

    @Override
    public @NotNull @Unmodifiable List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      return getLocation(protocol, path, null, project, scope);
    }

    @Override
    public @NotNull @Unmodifiable List<Location> getLocation(@NotNull String protocol,
                                                             @NotNull String path,
                                                             @Nullable String metainfo,
                                                             @NotNull Project project,
                                                             @NotNull GlobalSearchScope scope) {
      if (URLUtil.FILE_PROTOCOL.equals(protocol)) {
        return FileUrlProvider.INSTANCE.getLocation(protocol, path, project, scope);
      }
      else if (DumbService.getInstance(project).isUsableInCurrentContext(myLocator)) {
        return myLocator.getLocation(protocol, path, metainfo, project, scope);
      }
      else {
        return Collections.emptyList();
      }
    }

    @Override
    public @NotNull @Unmodifiable List<Location> getLocation(@NotNull String stacktraceLine, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      return myLocator.getLocation(stacktraceLine, project, scope);
    }

    @Override
    public @NotNull ModificationTracker getLocationCacheModificationTracker(@NotNull Project project) {
      return myLocator.getLocationCacheModificationTracker(project);
    }
  }

  //<editor-fold desc="Deprecated stuff.">
  /** @deprecated use {@link #createConsole(String, TestConsoleProperties)} */
  @Deprecated(forRemoval = true)
  @SuppressWarnings("unused")
  public static SMTRunnerConsoleView createConsoleWithCustomLocator(@NotNull String testFrameworkName,
                                                                    @NotNull TestConsoleProperties consoleProperties,
                                                                    ExecutionEnvironment environment,
                                                                    @Nullable TestLocationProvider locator,
                                                                    boolean idBasedTreeConstruction,
                                                                    @Nullable TestProxyFilterProvider filterProvider) {
    String splitterPropertyName = getSplitterPropertyName(testFrameworkName);
    SMTRunnerConsoleView consoleView = new SMTRunnerConsoleView(consoleProperties, splitterPropertyName);
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
    return consoleView;
  }

  /**
   * @deprecated should be removed with createConsoleWithCustomLocator()
   */
  @SuppressWarnings("rawtypes")
  @Deprecated(forRemoval = true)
  private static final class CompositeTestLocationProvider implements SMTestLocator {
    private final TestLocationProvider myPrimaryLocator;

    private CompositeTestLocationProvider(@Nullable TestLocationProvider primaryLocator) {
      myPrimaryLocator = primaryLocator;
    }

    @Override
    public @NotNull List<Location> getLocation(@NotNull String protocol, @NotNull String path, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      if (myPrimaryLocator != null && DumbService.getInstance(project).isUsableInCurrentContext(myPrimaryLocator)) {
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

      for (TestLocationProvider provider : TestLocationProvider.EP_NAME.getExtensionList()) {
        if (DumbService.getInstance(project).isUsableInCurrentContext(provider)) {
          List<Location> locations = provider.getLocation(protocol, path, project);
          if (!locations.isEmpty()) {
            return locations;
          }
        }
      }

      return Collections.emptyList();
    }
  }
  //</editor-fold>
}