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
import com.intellij.execution.impl.ConsoleViewImpl;
import com.intellij.execution.testframework.*;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.Disposable;

public class TestsOutputConsolePrinter implements Printer, Disposable {
  private final ConsoleView myConsole;
  private final TestConsoleProperties myProperties;
  private ChangingPrintable myCurrentPrintable = ChangingPrintable.DEAF;
  private Printer myOutput;

  // After pause action has been invoked -  all output will be redirected to special
  // myDeferingPrinter which will dump all buffered data after user will continue process.
  private final DeferingPrinter myDeferingPrinter = new DeferingPrinter(false);

  // It seems it is storage for uncaptured output by other printers (e.g. test proxies).
  // To prevent duplicated output collectioning output on this printer must be paused
  // (i.e. setCollectOutput(false)) after additional printer have been attached. You can
  // continue to collect output after additional printers will be deattached(e.g. test runner stops
  // sending events to test proxies).

  // If output collection was enabled for this console printer - all output will be collected in
  // myOutputStorage component. Otherwise no output will be stored.
  // 'myCurrentOutputStorage' printer is used for displaying whole output for test's root
  private final DeferingPrinter myOutputStorage = new DeferingPrinter(true);
  private Printer myCurrentOutputStorage = myOutputStorage;

  private int myMarkOffset = 0;

  private final TestFrameworkPropertyListener<Boolean> myPropertyListener = new TestFrameworkPropertyListener<Boolean>() {
        public void onChanged(final Boolean value) {
          if (!value.booleanValue()) myMarkOffset = 0;
        }
      };

  public TestsOutputConsolePrinter(final ConsoleView console, final TestConsoleProperties properties) {
    myConsole = console;
    myProperties = properties;
    myProperties.addListener(TestConsoleProperties.SCROLL_TO_STACK_TRACE, myPropertyListener);
    myOutput = this;
  }

  public ConsoleView getConsole() {
    return myConsole;
  }

  public boolean isPaused() {
    return myOutput != this;
  }

  public void pause(final boolean doPause) {
    if (doPause)
      myOutput = myDeferingPrinter;
    else {
      myOutput = this;
      myDeferingPrinter.printOn(myOutput);
    }
  }

  public void print(final String text, final ConsoleViewContentType contentType) {
    myConsole.print(text, contentType);
  }

  public void onNewAvailable(final Printable printable) {
    printable.printOn(myCurrentOutputStorage);
    printable.printOn(myOutput);
  }

  /**
   * Clears console, prints output of selected test and scrolls to beginning
   * of output.
   * This method must be invoked in Event Dispatch Thread
   * @param test Selected test
   */
  public void updateOnTestSelected(final PrintableTestProxy test) {
    if (myCurrentPrintable == test) {
      return;
    }
    myCurrentPrintable.setPrintLinstener(DEAF);
    myConsole.clear();
    myMarkOffset = 0;
    if (test == null) {
      myCurrentPrintable = ChangingPrintable.DEAF;
      return;
    }
    myCurrentPrintable = test;
    myCurrentPrintable.setPrintLinstener(this);
    if (test.isRoot()) {
      myOutputStorage.printOn(this);
    }
    myCurrentPrintable.printOn(this);
    scrollToBeginning();
    if (myConsole instanceof ConsoleViewImpl) {
      ((ConsoleViewImpl)myConsole).foldImmediately();
    }
  }

  public void printHyperlink(final String text, final HyperlinkInfo info) {
    myConsole.printHyperlink(text, info);
  }

  public void mark() {
    if (TestConsoleProperties.SCROLL_TO_STACK_TRACE.value(myProperties))
      myMarkOffset = myConsole.getContentSize();
  }

  public void dispose() {
    myProperties.removeListener(TestConsoleProperties.SCROLL_TO_STACK_TRACE, myPropertyListener);
  }
  
  public void setCollectOutput(final boolean doCollect) {
    myCurrentOutputStorage = doCollect ? myOutputStorage : DEAF;
  }

  public boolean canPause() {
    if (myCurrentPrintable instanceof AbstractTestProxy) {
      final AbstractTestProxy test = (AbstractTestProxy)myCurrentPrintable;
      return test.isInProgress();
    }
    return false;
  }

  protected void scrollToBeginning() {
    myConsole.performWhenNoDeferredOutput(new Runnable() {
      public void run() {
        myConsole.scrollTo(myMarkOffset);
      }
    });
  }
}