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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Processes events of test runner in general text-based form.
 * <p/>
 * Test name should be unique for all suites - e.g. it can consist of a suite name and a name of a test method.
 *
 * <p/>
 * Threading information:
 * <ul>
 *   <li>{@link #onUncapturedOutput(String, Key)} can be called from output reader created whether for normal or error output as well as command line can be printed from pooled thread which started the test process;</li>
 *   <li>all other events should be processed in the same output reader thread</li>
 *   <li>{@link #dispose()} is called from EDT</li>
 * </ul>
 *
 */
public abstract class GeneralTestEventsProcessor implements Disposable {
  private static final Logger LOG = Logger.getInstance(GeneralTestEventsProcessor.class.getName());
  protected final SMTRunnerEventsListener myEventPublisher;
  protected final SMTestProxy.SMRootTestProxy myTestsRootProxy;
  protected SMTestLocator myLocator = null;
  private final String myTestFrameworkName;
  protected List<SMTRunnerEventsListener> myListenerAdapters = new CopyOnWriteArrayList<>();

  protected boolean myTreeBuildBeforeStart = false;

  public GeneralTestEventsProcessor(@NotNull Project project,
                                    @NotNull String testFrameworkName,
                                    @NotNull SMTestProxy.SMRootTestProxy testsRootProxy) {
    myEventPublisher = project.getMessageBus().syncPublisher(SMTRunnerEventsListener.TEST_STATUS);
    myTestFrameworkName = testFrameworkName;
    myTestsRootProxy = testsRootProxy;
  }
  // tree construction events

  public void onRootPresentationAdded(final String rootName, final String comment, final String rootLocation) {
    myTestsRootProxy.setPresentation(rootName);
    myTestsRootProxy.setComment(comment);
    myTestsRootProxy.setRootLocationUrl(rootLocation);
    if (myLocator != null) {
      myTestsRootProxy.setLocator(myLocator);
    }
    myEventPublisher.onRootPresentationAdded(myTestsRootProxy, rootName, comment, rootLocation);
  }

  public void onSetNodeProperty(final @NotNull TestSetNodePropertyEvent event) {
    logProblem("Event unsupported: " + event);
  }

  protected SMTestProxy createProxy(String testName, String locationHint, String metaInfo, String id, String parentNodeId) {
    return new SMTestProxy(testName, false, locationHint, metaInfo, false);
  }

  protected SMTestProxy createSuite(String suiteName, String locationHint, String metaInfo, String id, String parentNodeId) {
    return new SMTestProxy(suiteName, true, locationHint, metaInfo, false);
  }

  protected final List<Runnable> myBuildTreeRunnables = new ArrayList<>();

  public void onSuiteTreeNodeAdded(final String testName, final String locationHint, final String metaInfo, String id, String parentNodeId) {
      onSuiteTreeNodeAdded(false,
                           testName,
                           locationHint,
                           metaInfo,
                           id,
                           parentNodeId);
  }

  /**
   * Populates the test result tree by nodes in "no run" state.
   * The node type can be suite or test. The node type, name, and location cannot be changes by
   * followed onSuiteStarted/onTestStarted calls.
   *
   * @param isSuite      the node type: true for suite, false for test
   * @param testName     the presentable test name visible to the user
   * @param locationHint the location info that is used for navigation to the suite/test source code
   * @param metaInfo     additional information of any type
   * @param id           the node identifier in the test result tree
   * @param parentNodeId the parent node identifier in the test result tree
   */
  public void onSuiteTreeNodeAdded(final boolean isSuite,
                                   final String testName,
                                   final String locationHint,
                                   final String metaInfo,
                                   String id,
                                   String parentNodeId) {
    myTreeBuildBeforeStart = true;
    myBuildTreeRunnables.add(() -> {
      final SMTestProxy testProxy = isSuite
                                    ? createSuite(testName, locationHint, metaInfo, id, parentNodeId)
                                    : createProxy(testName, locationHint, metaInfo, id, parentNodeId);
      testProxy.setTreeBuildBeforeStart();
      if (myLocator != null) {
        testProxy.setLocator(myLocator);
      }
      myEventPublisher.onSuiteTreeNodeAdded(testProxy, isSuite, id, parentNodeId);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onSuiteTreeNodeAdded(testProxy, isSuite, id, parentNodeId);
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
      myEventPublisher.onSuiteTreeStarted(newSuite, id, parentNodeId);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onSuiteTreeStarted(newSuite, id, parentNodeId);
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
    myEventPublisher.onBuildTreeEnded(myTestsRootProxy);
  }

  public final void onDurationStrategyChanged(@NotNull final TestDurationStrategy durationStrategy) {
    myTestsRootProxy.setDurationStrategy(durationStrategy);
  }

  private static void processTreeBuildEvents(final List<? extends Runnable> runnables) {
    for (Runnable runnable : runnables) {
      runnable.run();
    }
    runnables.clear();
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
    fireOnTestStarted(testProxy, null, null);
  }
  protected void fireOnTestStarted(SMTestProxy testProxy, @Nullable String nodeId, @Nullable String parentNodeId) {
    myEventPublisher.onTestStarted(testProxy, nodeId, parentNodeId);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestStarted(testProxy, nodeId, parentNodeId);
    }
  }

  public abstract void onTestFinished(@NotNull TestFinishedEvent testFinishedEvent);
  protected void fireOnTestFinished(SMTestProxy testProxy) {
    fireOnTestFinished(testProxy, null);
  }
  protected void fireOnTestFinished(SMTestProxy testProxy, @Nullable String nodeId) {
    myEventPublisher.onTestFinished(testProxy, nodeId);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestFinished(testProxy, nodeId);
    }
  }

  public abstract void onTestFailure(@NotNull TestFailedEvent testFailedEvent);
  protected void fireOnTestFailed(SMTestProxy testProxy) {
    fireOnTestFailed(testProxy, null);
  }
  protected void fireOnTestFailed(SMTestProxy testProxy, @Nullable String nodeId) {
    myEventPublisher.onTestFailed(testProxy, nodeId);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestFailed(testProxy, nodeId);
    }
  }

  public abstract void onTestIgnored(@NotNull TestIgnoredEvent testIgnoredEvent);
  protected void fireOnTestIgnored(SMTestProxy testProxy) {
    fireOnTestIgnored(testProxy, null);
  }
  protected void fireOnTestIgnored(SMTestProxy testProxy, @Nullable String nodeId) {
    myEventPublisher.onTestIgnored(testProxy, nodeId);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestIgnored(testProxy, nodeId);
    }
  }

  public abstract void onTestOutput(@NotNull TestOutputEvent testOutputEvent);

  public abstract void onSuiteStarted(@NotNull TestSuiteStartedEvent suiteStartedEvent);
  protected void fireOnSuiteStarted(SMTestProxy newSuite) {
    fireOnSuiteStarted(newSuite, null, null);
  }
  protected void fireOnSuiteStarted(SMTestProxy newSuite, @Nullable String nodeId, @Nullable String parentNodeId) {
    myEventPublisher.onSuiteStarted(newSuite, nodeId, parentNodeId);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onSuiteStarted(newSuite, nodeId, parentNodeId);
    }
  }

  public abstract void onSuiteFinished(@NotNull TestSuiteFinishedEvent suiteFinishedEvent);
  protected void fireOnSuiteFinished(SMTestProxy mySuite) {
    fireOnSuiteFinished(mySuite, null);
  }
  protected void fireOnSuiteFinished(SMTestProxy mySuite, @Nullable String nodeId) {
    myEventPublisher.onSuiteFinished(mySuite, nodeId);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onSuiteFinished(mySuite, nodeId);
    }
  }

  public abstract void onUncapturedOutput(@NotNull String text, Key outputType);

  public abstract void onError(@NotNull String localizedMessage, @Nullable String stackTrace, boolean isCritical);

  protected static void fireOnTestsReporterAttached(SMTestProxy.SMRootTestProxy rootNode) {
    rootNode.setTestsReporterAttached();
  }

  public void onFinishTesting() { }

  protected void fireOnTestingFinished(SMTestProxy.SMRootTestProxy root) {
    myEventPublisher.onTestingFinished(root);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onTestingFinished(root);
    }
  }

  protected void fireOnBeforeTestingFinished(@NotNull SMTestProxy.SMRootTestProxy root) {
    myEventPublisher.onBeforeTestingFinished(root);
     for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onBeforeTestingFinished(root);
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
    myEventPublisher.onCustomProgressTestsCategory(categoryName, testCount);
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onCustomProgressTestsCategory(categoryName, testCount);
    }
  }

  public void onCustomProgressTestStarted() {
    myEventPublisher.onCustomProgressTestStarted();
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onCustomProgressTestStarted();
    }
  }

  public void onCustomProgressTestFinished() {
    myEventPublisher.onCustomProgressTestFinished();
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onCustomProgressTestFinished();
    }
  }

  public void onCustomProgressTestFailed() {
    myEventPublisher.onCustomProgressTestFailed();
    for (SMTRunnerEventsListener adapter : myListenerAdapters) {
      adapter.onCustomProgressTestFailed();
    }
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
    disconnectListeners();
  }

  protected void disconnectListeners() {
    myListenerAdapters.clear();
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
