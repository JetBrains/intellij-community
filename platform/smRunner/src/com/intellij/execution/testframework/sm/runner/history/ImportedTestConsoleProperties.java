// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner.history;

import com.intellij.execution.Executor;
import com.intellij.execution.Location;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.actions.AbstractRerunFailedTestsAction;
import com.intellij.execution.testframework.sm.SMCustomMessagesParsing;
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter;
import com.intellij.execution.testframework.sm.runner.SMTRunnerConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.TestProxyFilterProvider;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

@ApiStatus.Internal
public class ImportedTestConsoleProperties extends SMTRunnerConsoleProperties implements SMCustomMessagesParsing {
  private final @Nullable SMTRunnerConsoleProperties myProperties;
  private final File myFile;
  private final ProcessHandler myHandler;

  public ImportedTestConsoleProperties(@Nullable SMTRunnerConsoleProperties properties,
                                       File file,
                                       ProcessHandler handler,
                                       Project project, RunProfile runConfiguration,
                                       String frameworkName,
                                       Executor executor) {
    super(project, runConfiguration, frameworkName, executor);
    myProperties = properties;
    myFile = file;
    myHandler = handler;
  }

  @Override
  public OutputToGeneralTestEventsConverter createTestEventsConverter(@NotNull String testFrameworkName,
                                                                      @NotNull TestConsoleProperties consoleProperties) {
    return new ImportedToGeneralTestEventsConverter(testFrameworkName, consoleProperties, myFile, myHandler);
  }

  @Override
  public boolean isIdBasedTestTree() {
    return false;
  }

  @Override
  public boolean isPrintTestingStartedTime() {
    return false;
  }

  @Override
  public @Nullable Navigatable getErrorNavigatable(@NotNull Location<?> location,
                                                   @NotNull String stacktrace) {
    return myProperties == null ? null : myProperties.getErrorNavigatable(location, stacktrace);
  }

  @Override
  public void addStackTraceFilter(Filter filter) {
    if (myProperties != null) {
      myProperties.addStackTraceFilter(filter);
    }
  }

  @Override
  public boolean fixEmptySuite() {
    return myProperties != null && myProperties.fixEmptySuite();
  }

  @Override
  public @Nullable SMTestLocator getTestLocator() {
    return myProperties == null ? null : myProperties.getTestLocator();
  }

  @Override
  public @Nullable TestProxyFilterProvider getFilterProvider() {
    return myProperties == null ? null : myProperties.getFilterProvider();
  }

  @Override
  public @Nullable AbstractRerunFailedTestsAction createRerunFailedTestsAction(ConsoleView consoleView) {
    return myProperties == null ? null : myProperties.createRerunFailedTestsAction(consoleView);
  }

  @Override
  public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) {
    if (myProperties != null) {
      myProperties.appendAdditionalActions(actionGroup, parent, this);
    }
  }

  @Override
  public int getSelectionMode() {
    return myProperties != null ? myProperties.getSelectionMode() : super.getSelectionMode();
  }
}
