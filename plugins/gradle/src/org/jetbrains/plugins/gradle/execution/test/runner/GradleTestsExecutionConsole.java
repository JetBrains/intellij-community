/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.TestConsoleProperties;
import com.intellij.execution.testframework.sm.runner.SMTestLocator;
import com.intellij.execution.testframework.sm.runner.SMTestProxy;
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView;
import com.intellij.execution.testframework.sm.runner.ui.SMTestRunnerResultsForm;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 10/28/2015
 */
public class GradleTestsExecutionConsole implements ConsoleView {
  private Map<String, SMTestProxy> testsMap = ContainerUtil.newHashMap();
  private StringBuilder myBuffer = new StringBuilder();
  private SMTRunnerConsoleView myExecutionConsole;

  public GradleTestsExecutionConsole(SMTRunnerConsoleView executionConsole) {
    myExecutionConsole = executionConsole;
  }

  public Map<String, SMTestProxy> getTestsMap() {
    return testsMap;
  }

  public StringBuilder getBuffer() {
    return myBuffer;
  }

  @Override
  public void print(@NotNull String s, @NotNull ConsoleViewContentType contentType) {
    myExecutionConsole.print(s, contentType);
  }

  @Override
  public void clear() {
    myExecutionConsole.clear();
  }

  @Override
  public void scrollTo(int offset) {
    myExecutionConsole.scrollTo(offset);
  }

  @Override
  public void attachToProcess(ProcessHandler processHandler) {
    myExecutionConsole.attachToProcess(processHandler);
  }

  @Override
  public void setOutputPaused(boolean value) {
    myExecutionConsole.setOutputPaused(value);
  }

  @Override
  public boolean isOutputPaused() {
    return myExecutionConsole.isOutputPaused();
  }

  @Override
  public boolean hasDeferredOutput() {
    return myExecutionConsole.hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(Runnable runnable) {
    myExecutionConsole.performWhenNoDeferredOutput(runnable);
  }

  @Override
  public void setHelpId(String helpId) {
    myExecutionConsole.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(Filter filter) {
    myExecutionConsole.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(String hyperlinkText, HyperlinkInfo info) {
    myExecutionConsole.printHyperlink(hyperlinkText, info);
  }

  @Override
  public int getContentSize() {
    return myExecutionConsole.getContentSize();
  }

  @Override
  public boolean canPause() {
    return myExecutionConsole.canPause();
  }

  @NotNull
  @Override
  public AnAction[] createConsoleActions() {
    return myExecutionConsole.createConsoleActions();
  }

  @Override
  public void allowHeavyFilters() {
    myExecutionConsole.allowHeavyFilters();
  }

  @Override
  public JComponent getComponent() {
    return myExecutionConsole.getComponent();
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myExecutionConsole.getPreferredFocusableComponent();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myExecutionConsole);
  }

  public SMTestRunnerResultsForm getResultsViewer() {
    return myExecutionConsole.getResultsViewer();
  }

  public TestConsoleProperties getProperties() {
    return myExecutionConsole.getProperties();
  }

  public SMTestLocator getUrlProvider() {
    return GradleUrlProvider.INSTANCE;
  }
}
