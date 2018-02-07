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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.TransferToEDTQueue;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Processes events of test runner in general text-based form.
 * <p/>
 * Test name should be unique for all suites - e.g. it can consist of a suite name and a name of a test method.
 *
 * @author: Roman Chernyatchik
 */
public abstract class GeneralTestEventsProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(GeneralTestEventsProcessor.class.getName());
  protected final SMTRunnerEventsListener myEventPublisher;
  protected final SMTestProxy.SMRootTestProxy myTestsRootProxy;
  protected SMTestLocator myLocator = null;
  private final String myTestFrameworkName;
  private final Project myProject;
  private TransferToEDTQueue<Runnable> myTransferToEDTQueue;
  protected List<SMTRunnerEventsListener> myListenerAdapters = new ArrayList<>();

  protected boolean myTreeBuildBeforeStart = false;

  public GeneralTestEventsProcessor(Project project, @NotNull String testFrameworkName, @NotNull SMTestProxy.SMRootTestProxy testsRootProxy) {
    myProject = project;
    myEventPublisher = project.getMessageBus().syncPublisher(SMTRunnerEventsListener.TEST_STATUS);
    myTestFrameworkName = testFrameworkName;
    myTransferToEDTQueue = new TransferToEDTQueue<>("SM queue", runnable -> {
      runnable.run();
      return true;
    }, project.getDisposed());
    myTestsRootProxy = testsRootProxy;
  }
  // tree construction events

  public void onRootPresentationAdded(final String rootName, final String comment, final String rootLocation) {
    addToInvokeLater(() -> {
      myTestsRootProxy.setPresentation(rootName);
      myTestsRootProxy.setComment(comment);
      myTestsRootProxy.setRootLocationUrl(rootLocation);
      if (myLocator != null) {
        myTestsRootProxy.setLocator(myLocator);
      }
    });
  }

  protected SMTestProxy createProxy(String testName, String locationHint, String metaInfo, String id, String parentNodeId) {
    return new SMTestProxy(testName, false, locationHint, metaInfo, false);
  }

  protected SMTestProxy createSuite(String suiteName, String locationHint, String metaInfo, String id, String parentNodeId) {
    return new SMTestProxy(suiteName, true, locationHint, metaInfo, false);
  }

  protected final List<Runnable> myBuildTreeRunnables = new ArrayList<>();

  public void onSuiteTreeNodeAdded(final String testName, final String locationHint, final String metaInfo, String id, String parentNodeId) {
    myTreeBuildBeforeStart = true;
    myBuildTreeRunnables.add(() -> {
      final SMTestProxy testProxy = createProxy(testName, locationHint, metaInfo, id, parentNodeId);
      testProxy.setTreeBuildBeforeStart();
      if (myLocator != null) {
        testProxy.setLocator(myLocator);
      }
      myEventPublisher.onSuiteTreeNodeAdded(testProxy);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onSuiteTreeNodeAdded(testProxy);
      }
      //ensure root node gets the flag when merged with a single child
      testProxy.getParent().setTreeBuildBeforeStart();
    });
  }

  public void onSuiteTreeStarted(final String suiteName, final String locationHint, String metaInfo, String id, String parentNodeId) {
    myTreeBuildBeforeStart = true;
    myBuildTreeRunnables.add(() -> {
      final SMTestProxy newSuite = createSuite(suiteName, locationHint, metaInfo, id, parentNodeId);
      if (myLocator != null) {
        newSuite.setLocator(myLocator);
      }
      newSuite.setTreeBuildBeforeStart();
      myEventPublisher.onSuiteTreeStarted(newSuite);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onSuiteTreeStarted(newSuite);
      }
    });
  }

  public void onSuiteTreeEnded(final String suiteName) {
    if (myBuildTreeRunnables.size() > 100) {
      final ArrayList<Runnable> runnables = new ArrayList<>(myBuildTreeRunnables);
      myBuildTreeRunnables.clear();
      processTreeBuildEvents(runnables);
    }
  }

  public void onBuildTreeEnded() {
    final ArrayList<Runnable> runnables = new ArrayList<>(myBuildTreeRunnables);
    myBuildTreeRunnables.clear();
    processTreeBuildEvents(runnables);
  }

  private void processTreeBuildEvents(final List<Runnable> runnables) {
    addToInvokeLater(() -> {
      for (Runnable runnable : runnables) {
        runnable.run();
      }
      runnables.clear();
    });
  }

  // progress events

  public abstract void onStartTesting();
  protected void fireOnTestingStarted(SMTestProxy.SMRootTestProxy node) {
    myEventPublisher.onTestingStarted(node);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestingStarted(node);
    }
  }

  public abstract void onTestsCountInSuite(final int count);
  protected void fireOnTestsCountInSuite(int count) {
    myEventPublisher.onTestsCountInSuite(count);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestsCountInSuite(count);
    }
  }

  public abstract void onTestStarted(@NotNull TestStartedEvent testStartedEvent);
  protected void fireOnTestStarted(SMTestProxy testProxy) {
    myEventPublisher.onTestStarted(testProxy);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestStarted(testProxy);
    }
  }

  public abstract void onTestFinished(@NotNull TestFinishedEvent testFinishedEvent);
  protected void fireOnTestFinished(SMTestProxy testProxy) {
    myEventPublisher.onTestFinished(testProxy);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestFinished(testProxy);
    }
  }

  public abstract void onTestFailure(@NotNull TestFailedEvent testFailedEvent);
  protected void fireOnTestFailed(SMTestProxy testProxy) {
    myEventPublisher.onTestFailed(testProxy);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestFailed(testProxy);
    }
  }

  public abstract void onTestIgnored(@NotNull TestIgnoredEvent testIgnoredEvent);
  protected void fireOnTestIgnored(SMTestProxy testProxy) {
    myEventPublisher.onTestIgnored(testProxy);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestIgnored(testProxy);
    }
  }

  public abstract void onTestOutput(@NotNull TestOutputEvent testOutputEvent);

  public abstract void onSuiteStarted(@NotNull TestSuiteStartedEvent suiteStartedEvent);
  protected void fireOnSuiteStarted(SMTestProxy newSuite) {
    myEventPublisher.onSuiteStarted(newSuite);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onSuiteStarted(newSuite);
    }
  }
  
  public abstract void onSuiteFinished(@NotNull TestSuiteFinishedEvent suiteFinishedEvent);
  protected void fireOnSuiteFinished(SMTestProxy mySuite) {
    myEventPublisher.onSuiteFinished(mySuite);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onSuiteFinished(mySuite);
    }
  }

  public abstract void onUncapturedOutput(@NotNull String text, Key outputType);

  public abstract void onError(@NotNull String localizedMessage, @Nullable String stackTrace, boolean isCritical);

  protected static void fireOnTestsReporterAttached(SMTestProxy.SMRootTestProxy rootNode) {
    rootNode.setTestsReporterAttached();
  }

  public void onFinishTesting() {
    stopEventProcessing();
  }

  protected void fireOnTestingFinished(SMTestProxy.SMRootTestProxy root) {
    myEventPublisher.onTestingFinished(root);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestingFinished(root);
    }
  }

  // custom progress statistics

  /**
   * @param categoryName If isn't empty then progress statistics will use only custom start/failed events.
   *                     If name is null statistics will be switched to normal mode
   * @param testCount    0 will be considered as unknown tests number
   */
  public void onCustomProgressTestsCategory(@Nullable final String categoryName,
                                            final int testCount) {
    addToInvokeLater(() -> {
      myEventPublisher.onCustomProgressTestsCategory(categoryName, testCount);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onCustomProgressTestsCategory(categoryName, testCount);
      }
    });
  }

  public void onCustomProgressTestStarted() {
    addToInvokeLater(() -> {
      myEventPublisher.onCustomProgressTestStarted();
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onCustomProgressTestStarted();
      }
    });
  }

  public void onCustomProgressTestFinished() {
    addToInvokeLater(() -> {
      myEventPublisher.onCustomProgressTestFinished();
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onCustomProgressTestFinished();
      }
    });
  }

  public void onCustomProgressTestFailed() {
    addToInvokeLater(() -> {
      myEventPublisher.onCustomProgressTestFailed();
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onCustomProgressTestFailed();
      }
    });
  }

  // workflow/service methods

  public abstract void onTestsReporterAttached();

  public void setLocator(@NotNull SMTestLocator locator) {
    myLocator = locator;
  }

  public void addEventsListener(@NotNull SMTRunnerEventsListener listener) {
    myListenerAdapters.add(listener);
  }

  public abstract void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider);

  @Override
  public void dispose() {
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      UIUtil.invokeAndWaitIfNeeded((Runnable)() -> {
        if (myProject.isDisposed()) {
          return;
        }
        myTransferToEDTQueue.drain();
      });
    }
  }

  protected void disconnectListeners() {
    myListenerAdapters.clear();
  }

  public Condition getDisposedCondition() {
    return Condition.FALSE;
  }

  public void addToInvokeLater(final Runnable runnable) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!myProject.isDisposed()) {
          runnable.run();
        }
      });
    }
    else if (application.isHeadlessEnvironment() || SwingUtilities.isEventDispatchThread()) {
      runnable.run();
    }
    else {
      myTransferToEDTQueue.offer(runnable);
    }
  }

  public void stopEventProcessing() {
    UIUtil.invokeLaterIfNeeded(() -> {
      if (myProject.isDisposed()) {
        return;
      }
      myTransferToEDTQueue.drain();
    });
  }


  protected static <T> boolean isTreeComplete(Collection<T> runningTests, SMTestProxy.SMRootTestProxy rootNode) {
    if (!runningTests.isEmpty()) {
      return false;
    }
    List<? extends SMTestProxy> children = rootNode.getChildren();
    for (SMTestProxy child : children) {
      if (!child.isFinal() || child.wasTerminated()) {
        return false;
      }
    }
    return true;
  }

  protected void logProblem(final String msg) {
    logProblem(LOG, msg, myTestFrameworkName);
  }
  
  protected void logProblem(String msg, boolean throwError) {
    logProblem(LOG, msg, throwError, myTestFrameworkName);
  }

  public static String getTFrameworkPrefix(final String testFrameworkName) {
    return "[" + testFrameworkName + "]: ";
  }

  public static void logProblem(final Logger log, final String msg, final String testFrameworkName) {
    logProblem(log, msg, SMTestRunnerConnectionUtil.isInDebugMode(), testFrameworkName);
  }

  public static void logProblem(final Logger log, final String msg, boolean throwError, final String testFrameworkName) {
    final String text = getTFrameworkPrefix(testFrameworkName) + msg;
    if (throwError) {
      log.error(text);
    }
    else {
      log.warn(text);
    }
  }
}
