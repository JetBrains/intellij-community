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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.Processor;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author: Roman Chernyatchik
 * <p/>
 * Processes events of test runner in general text-based form
 * <p/>
 * NB: Test name should be unique for all suites. E.g. it can consist of suite name
 * and name of test method
 */
public abstract class GeneralTestEventsProcessor implements Disposable {
  private TransferToEDTQueue<Runnable> myTransferToEDTQueue =
    new TransferToEDTQueue<Runnable>("SM queue", new Processor<Runnable>() {
      @Override
      public boolean process(Runnable runnable) {
        runnable.run();
        return true;
      }
    }, getDisposedCondition(), 300);

  public abstract void onStartTesting();

  public abstract void onTestsCountInSuite(final int count);

  public abstract void onTestStarted(@NotNull TestStartedEvent testStartedEvent);

  public abstract void onTestFinished(@NotNull TestFinishedEvent testFinishedEvent);

  public abstract void onTestFailure(@NotNull TestFailedEvent testFailedEvent);

  public abstract void onTestIgnored(@NotNull TestIgnoredEvent testIgnoredEvent);

  public abstract void onTestOutput(@NotNull TestOutputEvent testOutputEvent);

  public abstract void onSuiteStarted(@NotNull TestSuiteStartedEvent suiteStartedEvent);

  public abstract void onSuiteFinished(@NotNull TestSuiteFinishedEvent suiteFinishedEvent);

  public abstract void onUncapturedOutput(@NotNull final String text,
                                          final Key outputType);

  public abstract void onError(@NotNull final String localizedMessage,
                               @Nullable final String stackTrace,
                               final boolean isCritical);

  // Custom progress statistics

  /**
   * @param categoryName If isn't empty then progress statistics will use only custom start/failed events.
   *                     If name is null statistics will be switched to normal mode
   * @param testCount    - 0 will be considered as unknown tests number
   */
  public abstract void onCustomProgressTestsCategory(@Nullable final String categoryName,
                                                     final int testCount);

  public abstract void onCustomProgressTestStarted();

  public abstract void onCustomProgressTestFailed();

  public abstract void onTestsReporterAttached();

  public abstract void setLocator(@NotNull TestLocationProvider locator);

  public abstract void addEventsListener(@NotNull SMTRunnerEventsListener viewer);

  public abstract void onFinishTesting();

  public abstract void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider);

  @Override
  public void dispose() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          myTransferToEDTQueue.drain();
        }
      });
    }
  }

  public Condition getDisposedCondition() {
    return Condition.FALSE;
  }

  /**
   * Adds runnable to Event Dispatch Queue
   * if we aren't in UnitTest of Headless environment mode
   *
   * @param runnable Runnable
   */
  public void addToInvokeLater(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    final boolean unitTestMode = application.isUnitTestMode();
    if (unitTestMode) {
      UIUtil.invokeLaterIfNeeded(runnable);
    } else if (application.isHeadlessEnvironment() || SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      myTransferToEDTQueue.offer(runnable);
    }
  }
}
