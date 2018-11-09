/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.ide.HelpIdProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BaseTestsOutputConsoleView implements ConsoleView, ObservableConsoleView, HelpIdProvider {
  private ConsoleView myConsole;
  private TestsOutputConsolePrinter myPrinter;
  protected TestConsoleProperties myProperties;
  private TestResultsPanel myTestResultsPanel;

  public BaseTestsOutputConsoleView(final TestConsoleProperties properties, final AbstractTestProxy unboundOutputRoot) {
    myProperties = properties;

    myConsole = myProperties.createConsole();
    myPrinter = new TestsOutputConsolePrinter(this, properties, unboundOutputRoot);
    myProperties.setConsole(this);

    Disposer.register(this, myProperties);
    Disposer.register(this, myConsole);
  }

  public void initUI() {
    myTestResultsPanel = createTestResultsPanel();
    myTestResultsPanel.initUI();
    Disposer.register(this, myTestResultsPanel);
  }

  protected abstract TestResultsPanel createTestResultsPanel();

  @Override
  public void attachToProcess(final ProcessHandler processHandler) {
    myConsole.attachToProcess(processHandler);
  }

  @Override
  public void print(@NotNull final String text, @NotNull final ConsoleViewContentType contentType) {
    printNew(printer -> printer.print(text, contentType));
  }

  @Override
  public void allowHeavyFilters() {
  }

  @Override
  public void clear() {
    myConsole.clear();
  }

  @Override
  public void scrollTo(final int offset) {
    myConsole.scrollTo(offset);
  }

  @Override
  public void setOutputPaused(final boolean value) {
    if (myPrinter != null) {
      myPrinter.pause(value);
    }
  }

  @Override
  public boolean isOutputPaused() {
    //noinspection SimplifiableConditionalExpression
    return myPrinter == null ? true : myPrinter.isPaused();
  }

  @Override
  public boolean hasDeferredOutput() {
    return myConsole.hasDeferredOutput();
  }

  @Override
  public void performWhenNoDeferredOutput(@NotNull final Runnable runnable) {
    myConsole.performWhenNoDeferredOutput(runnable);
  }

  @Override
  public void setHelpId(@NotNull final String helpId) {
    myConsole.setHelpId(helpId);
  }

  @Override
  public void addMessageFilter(@NotNull final Filter filter) {
    myConsole.addMessageFilter(filter);
  }

  @Override
  public void printHyperlink(@NotNull final String hyperlinkText, final HyperlinkInfo info) {
    printNew(new HyperLink(hyperlinkText, info));
  }

  @Override
  public int getContentSize() {
    return myConsole.getContentSize();
  }

  @Override
  public boolean canPause() {
    return myPrinter != null && myPrinter.canPause() && myConsole.canPause();
  }

  @Override
  public JComponent getComponent() {
    return myTestResultsPanel;
  }

  @Override
  public JComponent getPreferredFocusableComponent() {
    return myTestResultsPanel;
  }

  @Override
  public void dispose() {
    myPrinter = null;
    myProperties = null;
    myConsole = null;
  }

  @Override
  public void addChangeListener(@NotNull final ChangeListener listener, @NotNull final Disposable parent) {
    if (myConsole instanceof ObservableConsoleView) {
      ((ObservableConsoleView)myConsole).addChangeListener(listener, parent);
    }
  }

  @Override
  @NotNull
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  @NotNull
  public ConsoleView getConsole() {
    return myConsole;
  }

  public TestsOutputConsolePrinter getPrinter() {
    return myPrinter;
  }

  private void printNew(final Printable printable) {
    if (myPrinter != null) {
      myPrinter.onNewAvailable(printable);
    }
  }

  public TestConsoleProperties getProperties() {
    return myProperties;
  }

  @Nullable
  @Override
  public String getHelpId() {
    return "reference.runToolWindow.testResultsTab";
  }
}
