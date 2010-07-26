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
package com.intellij.execution.testframework.ui;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ObservableConsoleView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class BaseTestsOutputConsoleView implements ConsoleView, ObservableConsoleView {
  private ConsoleView myConsole;
  private TestsOutputConsolePrinter myPrinter;
  protected TestConsoleProperties myProperties;
  protected TestResultsPanel myTestResultsPanel;

  public BaseTestsOutputConsoleView(final TestConsoleProperties properties, final AbstractTestProxy unboundOutputRoot) {
    myProperties = properties;
    myConsole = TextConsoleBuilderFactory.getInstance().createBuilder(properties.getProject(), myProperties.getScope()).getConsole();
    myPrinter = new TestsOutputConsolePrinter(myConsole, properties, unboundOutputRoot);
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

  public void print(final String s, final ConsoleViewContentType contentType) {
    printNew(new Printable() {
      public void printOn(final Printer printer) {
        printer.print(s, contentType);
      }
    });
  }

  public void clear() {
    myConsole.clear();
  }

  public void scrollTo(final int offset) {
    myConsole.scrollTo(offset);
  }

  public void setOutputPaused(final boolean value) {
    if (myPrinter != null) {
      myPrinter.pause(value);
    }
  }

  public boolean isOutputPaused() {
    //noinspection SimplifiableConditionalExpression
    return myPrinter == null ? true : myPrinter.isPaused();
  }

  public boolean hasDeferredOutput() {
    return myConsole.hasDeferredOutput();
  }

  public void performWhenNoDeferredOutput(final Runnable runnable) {
    myConsole.performWhenNoDeferredOutput(runnable);
  }

  public void setHelpId(final String helpId) {
    myConsole.setHelpId(helpId);
  }

  public void addMessageFilter(final Filter filter) {
    myConsole.addMessageFilter(filter);
  }

  public void printHyperlink(final String hyperlinkText, final HyperlinkInfo info) {
    printNew(new HyperLink(hyperlinkText, info));
  }

  public int getContentSize() {
    return myConsole.getContentSize();
  }

  public boolean canPause() {
    return myPrinter != null && myPrinter.canPause() && myConsole.canPause();
  }

  public JComponent getComponent() {
    return myTestResultsPanel;
  }

  public JComponent getPreferredFocusableComponent() {
    return myConsole.getPreferredFocusableComponent();
  }

  public void dispose() {
    myPrinter = null;
    myProperties = null;
    myConsole = null;
  }

  public void addChangeListener(final ChangeListener listener, final Disposable parent) {
    if (myConsole instanceof ObservableConsoleView) {
      ((ObservableConsoleView)myConsole).addChangeListener(listener, parent);
    } else {
      throw new UnsupportedOperationException(myConsole.getClass().getName());
    }
  }

  @NotNull
  public AnAction[] createConsoleActions() {
    return AnAction.EMPTY_ARRAY;
  }

  protected ConsoleView getConsole() {
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
}
