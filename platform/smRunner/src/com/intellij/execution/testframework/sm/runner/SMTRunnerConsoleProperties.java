// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.filters.CompositeFilter;
import com.intellij.execution.filters.FileHyperlinkInfo;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.FileUrlProvider;
import com.intellij.execution.testframework.sm.SMStacktraceParserEx;
import com.intellij.execution.testframework.sm.runner.history.actions.AbstractImportTestsAction;
import com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsFromFileAction;
import com.intellij.execution.testframework.sm.runner.history.actions.ImportTestsGroup;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Roman Chernyatchik
 * Use {@link SMRunnerConsolePropertiesProvider} so importer {@link AbstractImportTestsAction.ImportRunProfile#ImportRunProfile(VirtualFile, Project)}
 * would be able to create properties by read configuration and test navigation, rerun failed tests etc. would work on imported results
 */
public class SMTRunnerConsoleProperties extends TestConsoleProperties implements SMStacktraceParserEx {
  private final RunProfile myConfiguration;
  @NotNull private final String myTestFrameworkName;
  private final CompositeFilter myCustomFilter;
  private boolean myIdBasedTestTree = false;
  private boolean myPrintTestingStartedTime = true;

  /**
   * @param config
   * @param testFrameworkName Prefix for storage which keeps runner settings. E.g. "RubyTestUnit". 
   *                          Is used to distinguish problems of different test frameworks in logged exceptions
   * @param executor
   */
  public SMTRunnerConsoleProperties(@NotNull RunConfiguration config, @NlsSafe @NotNull String testFrameworkName, @NotNull Executor executor) {
    this(config.getProject(), config, testFrameworkName, executor);
  }

  public SMTRunnerConsoleProperties(@NotNull Project project,
                                    @NotNull RunProfile config,
                                    @NlsSafe @NotNull String testFrameworkName,
                                    @NotNull Executor executor) {
    super(new Storage.PropertiesComponentStorage(testFrameworkName + "Support.", PropertiesComponent.getInstance()), project, executor);
    myConfiguration = config;
    myTestFrameworkName = testFrameworkName;
    myCustomFilter = new CompositeFilter(project);
  }

  /**
   * If enabled, runner must add new line char (\n) before each TC message. This char is not reported to user.
   * @deprecated Fix your runner and stop adding "\n" before TC message.
   */
  @Deprecated(forRemoval = true)
  public boolean serviceMessageHasNewLinePrefix() {
    return false;
  }

  @Override
  public @NotNull RunProfile getConfiguration() {
    return myConfiguration;
  }

  @Override
  protected AnAction @Nullable [] createImportActions() {
    return new AnAction[] {new ImportTestsGroup(this), new ImportTestsFromFileAction()};
  }

  public boolean isIdBasedTestTree() {
    return myIdBasedTestTree;
  }

  /**
   * To switch between {@link GeneralIdBasedToSMTRunnerEventsConvertor} and 
   * {@link GeneralToSMTRunnerEventsConvertor}. Use first one if framework provides unique ids for tests
   */
  public void setIdBasedTestTree(boolean idBasedTestTree) {
    myIdBasedTestTree = idBasedTestTree;
  }

  public boolean isPrintTestingStartedTime() {
    return myPrintTestingStartedTime;
  }

  public void setPrintTestingStartedTime(boolean printTestingStartedTime) {
    myPrintTestingStartedTime = printTestingStartedTime;
  }

  public static class FileHyperlinkNavigatable implements Navigatable {
    private OpenFileDescriptor myFileDescriptor;
    private final FileHyperlinkInfo myFileHyperlinkInfo;

    public FileHyperlinkNavigatable(@NotNull FileHyperlinkInfo info) { myFileHyperlinkInfo = info; }

    public OpenFileDescriptor getOpenFileDescriptor() {
      if (myFileDescriptor == null) {
        myFileDescriptor = myFileHyperlinkInfo.getDescriptor();
      }
      return myFileDescriptor;
    }

    @Override
    public void navigate(boolean requestFocus) {
      getOpenFileDescriptor().navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
      return getOpenFileDescriptor().canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
      return getOpenFileDescriptor().canNavigateToSource();
    }
  }

  @Nullable
  @Override
  public Navigatable getErrorNavigatable(@NotNull Location<?> location, @NotNull String stacktrace) {
    if (myCustomFilter.isEmpty()) {
      return null;
    }

    // iterate stacktrace lines find first navigatable line using
    // stacktrace filters
    final int stacktraceLength = stacktrace.length();
    final String[] lines = StringUtil.splitByLines(stacktrace);
    for (String line : lines) {
      Filter.Result result;
      try {
        result = myCustomFilter.applyFilter(line, stacktraceLength);
      }
      catch (Throwable t) {
        throw new RuntimeException("Error while applying " + myCustomFilter + " to '" + line + "'", t);
      }
      final HyperlinkInfo info = result != null ? result.getFirstHyperlinkInfo() : null;
      if (info != null) {

        // covers 99% use existing cases
        if (info instanceof FileHyperlinkInfo) {
          return new FileHyperlinkNavigatable((FileHyperlinkInfo)info);
        }

        // otherwise
        Project project = location.getProject();
        return new Navigatable() {
          @Override
          public void navigate(boolean requestFocus) {
            info.navigate(project);
          }

          @Override
          public boolean canNavigate() {
            return true;
          }

          @Override
          public boolean canNavigateToSource() {
            return true;
          }
        };
      }
    }
    return null;
  }

  public void addStackTraceFilter(final Filter filter) {
    myCustomFilter.addFilter(filter);
  }

  /**
   * Called if no tests were detected in the suite. Show suggestion to change the configuration so some tests would be found
   */
  public boolean fixEmptySuite() {
    return false;
  }

  /**
   * @return custom test locator which would be combined with default {@link FileUrlProvider}
   */
  @Nullable
  public SMTestLocator getTestLocator() {
    return null;
  }

  @Nullable
  public TestProxyFilterProvider getFilterProvider() {
    return null;
  }

  @Nullable
  public AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return null;
  }

  @NotNull
  public String getTestFrameworkName() {
    return myTestFrameworkName;
  }

  /**
   * @return true to make test status progress indeterminate, e.g. if repeat count set to `until failure`, so it's impossible to predict total number of tests
   */
  public boolean isUndefined() {
    return false;
  }
}
