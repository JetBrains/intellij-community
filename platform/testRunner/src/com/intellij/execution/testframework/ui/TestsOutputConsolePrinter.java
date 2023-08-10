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

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.terminal.TerminalExecutionConsole;
import com.intellij.util.ui.EdtInvocationManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestsOutputConsolePrinter implements Printer, Disposable {
  private final ConsoleView myConsole;
  private final TestConsoleProperties myProperties;
  private final AbstractTestProxy myUnboundOutputRoot;
  private AbstractTestProxy myCurrentTest;

  // After pause action has been invoked -  all output will be redirected to special
  // myPausedPrinter which will dump all buffered data after user will continue process.
  private final DeferingPrinter myPausedPrinter = new DeferingPrinter();
  private boolean myPaused;

  private int myMarkOffset;

  private final TestFrameworkPropertyListener<Boolean> myPropertyListener = new TestFrameworkPropertyListener<>() {
    @Override
    public void onChanged(final Boolean value) {
      if (!value.booleanValue()) myMarkOffset = 0;
    }
  };
  private boolean myDisposed;

  public TestsOutputConsolePrinter(@NotNull BaseTestsOutputConsoleView testsOutputConsoleView, @NotNull TestConsoleProperties properties, final AbstractTestProxy unboundOutputRoot) {
    myConsole = testsOutputConsoleView.getConsole();
    myProperties = properties;
    myUnboundOutputRoot = unboundOutputRoot;
    myProperties.addListener(TestConsoleProperties.SCROLL_TO_STACK_TRACE, myPropertyListener);
  }

  public ConsoleView getConsole() {
    return myConsole;
  }

  public boolean isPaused() {
    return myPaused;
  }

  public void pause(final boolean doPause) {
    myPaused = doPause;
    if (!doPause) {
      myPausedPrinter.printAndForget(this);
    }
  }

  @Override
  public void print(final @NotNull String text, final @NotNull ConsoleViewContentType contentType) {
    myConsole.print(text, contentType);
  }

  @Override
  public void onNewAvailable(@NotNull final Printable printable) {
    printable.printOn(myPaused ? myPausedPrinter : this);
  }

  /**
   * Clears console, prints output of selected test and scrolls to beginning
   * of output.
   * This method must be invoked in Event Dispatch Thread
   * @param test Selected test
   */
  public void updateOnTestSelected(final AbstractTestProxy test) {
    if (myCurrentTest == test) {
      return;
    }
    if (myCurrentTest != null) {
      myCurrentTest.setPrinter(null);
    }
    myMarkOffset = 0;
    final Runnable clearRunnable = () -> myConsole.clear();
    if (test == null) {
      myCurrentTest = null;
      CompositePrintable.invokeInAlarm(clearRunnable);
      return;
    }
    myCurrentTest = test;
    myCurrentTest.setPrinter(this);
    final Runnable scrollRunnable = () -> scrollToBeginning();
    final AbstractTestProxy currentProxyOrRoot = getCurrentProxyOrRoot();
    CompositePrintable.invokeInAlarm(clearRunnable);
    currentProxyOrRoot.printOn(this);
    currentProxyOrRoot.printFromFrameworkOutputFile(this);
    CompositePrintable.invokeInAlarm(scrollRunnable);
  }

  private AbstractTestProxy getCurrentProxyOrRoot() {
    return isRoot() && myUnboundOutputRoot != null ? myUnboundOutputRoot : myCurrentTest;
  }

  public boolean isCurrent(CompositePrintable printable) {
    return myCurrentTest == printable || isRoot();
  }

  private boolean isRoot() {
    return isRoot(myCurrentTest);
  }

  private boolean isRoot(@Nullable AbstractTestProxy proxy) {
    return proxy != null && proxy.getParent() == myUnboundOutputRoot;
  }

  @Override
  public void printHyperlink(final @NotNull String text, final HyperlinkInfo info) {
    myConsole.printHyperlink(text, info);
  }

  @Override
  public void mark() {
    if (TestConsoleProperties.SCROLL_TO_STACK_TRACE.value(myProperties)) {
      if (myMarkOffset == 0 || !Registry.is("scroll.to.first.trace", true)) {
        myMarkOffset = myConsole.getContentSize();
      }
    }
  }

  @Override
  public void dispose() {
    myProperties.removeListener(TestConsoleProperties.SCROLL_TO_STACK_TRACE, myPropertyListener);
    myDisposed = true;
  }

  public boolean canPause() {
    return myCurrentTest != null && myCurrentTest.isInProgress();
  }

  protected void scrollToBeginning() {
    EdtInvocationManager.invokeLaterIfNeeded(() -> {
      if (!myDisposed) {
        myConsole.performWhenNoDeferredOutput(() -> {
          final AbstractTestProxy currentProxyOrRoot = getCurrentProxyOrRoot();
          if (currentProxyOrRoot != null && !currentProxyOrRoot.isInProgress()) {
            //do not scroll to any mark during run
            myConsole.scrollTo(myMarkOffset);
          }
        });
      }
    });
  }

  @Override
  public void printWithAnsiColoring(@NotNull String text, @NotNull Key processOutputType) {
    if (myConsole instanceof TerminalExecutionConsole) {
      // Terminal console handles ANSI escape sequences itself
      print(text, ConsoleViewContentType.getConsoleViewType(processOutputType));
    }
    else {
      Printer.super.printWithAnsiColoring(text, processOutputType);
    }
  }
  
  @Override
  public void printExpectedActualHeader(@NotNull String expected, @NotNull String actual) {
    myProperties.printExpectedActualHeader(this, expected, actual);
  }
}
