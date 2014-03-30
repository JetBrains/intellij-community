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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.testIntegration.TestLocationProvider;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * @author Sergey Simonchik
 */
public class GeneralIdBasedToSMTRunnerEventsConvertor extends GeneralTestEventsProcessor {
  private static final Logger LOG = Logger.getInstance(GeneralIdBasedToSMTRunnerEventsConvertor.class.getName());

  private final TIntObjectHashMap<Node> myNodeByIdMap = new TIntObjectHashMap<Node>();
  private final Set<Node> myRunningTestNodes = ContainerUtil.newHashSet();
  private final List<SMTRunnerEventsListener> myEventsListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final SMTestProxy.SMRootTestProxy myTestsRootProxy;
  private final Node myTestsRootNode;
  private final String myTestFrameworkName;
  private boolean myIsTestingFinished = false;
  private TestLocationProvider myLocator = null;
  private TestProxyPrinterProvider myTestProxyPrinterProvider = null;

  public GeneralIdBasedToSMTRunnerEventsConvertor(@NotNull SMTestProxy.SMRootTestProxy testsRootProxy,
                                                  @NotNull String testFrameworkName) {
    myTestsRootProxy = testsRootProxy;
    myTestsRootNode = new Node(0, null, testsRootProxy);
    myTestFrameworkName = testFrameworkName;
    myNodeByIdMap.put(myTestsRootNode.getId(), myTestsRootNode);
  }

  public void setLocator(@NotNull TestLocationProvider customLocator) {
    myLocator = customLocator;
  }

  public void addEventsListener(@NotNull SMTRunnerEventsListener listener) {
    myEventsListeners.add(listener);
  }

  public void onStartTesting() {
    addToInvokeLater(new Runnable() {
      public void run() {
        myTestsRootNode.setState(State.RUNNING);
        myTestsRootProxy.setStarted();
        fireOnTestingStarted();
      }
    });
  }

  @Override
  public void onTestsReporterAttached() {
    addToInvokeLater(new Runnable() {
      public void run() {
        myTestsRootProxy.setTestsReporterAttached();
      }
    });
  }

  public void onFinishTesting() {
    addToInvokeLater(new Runnable() {
      public void run() {
        if (myIsTestingFinished) {
          // has been already invoked!
          return;
        }
        myIsTestingFinished = true;

        // We don't know whether process was destroyed by user
        // or it finished after all tests have been run
        // Lets assume, if at finish all nodes except root suite have final state (passed, failed or ignored),
        // then all is ok otherwise process was terminated by user
        boolean completeTree = isTreeComplete();
        if (completeTree) {
          myTestsRootProxy.setFinished();
        } else {
          myTestsRootProxy.setTerminated();
        }
        if (!myRunningTestNodes.isEmpty()) {
          logProblem("Unexpected running nodes: " + myRunningTestNodes);
        }
        myNodeByIdMap.clear();
        myRunningTestNodes.clear();

        fireOnTestingFinished();
      }
    });
  }

  private boolean isTreeComplete() {
    if (!myRunningTestNodes.isEmpty()) {
      return false;
    }
    List<? extends SMTestProxy> children = myTestsRootProxy.getChildren();
    for (SMTestProxy child : children) {
      if (!child.isFinal() || child.wasTerminated()) {
        return false;
      }
    }
    return true;
  }

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
    myTestProxyPrinterProvider = printerProvider;
  }

  public void onTestStarted(@NotNull final TestStartedEvent testStartedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        doStartNode(testStartedEvent, false);
      }
    });
  }

  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        doStartNode(suiteStartedEvent, true);
      }
    });
  }

  private void doStartNode(@NotNull BaseStartedNodeEvent startedNodeEvent, boolean suite) {
    Node node = findNode(startedNodeEvent);
    if (node != null) {
      if (node.getState() == State.NOT_RUNNING && startedNodeEvent.isRunning()) {
        setNodeAndAncestorsRunning(node);
      }
      else {
        logProblem(startedNodeEvent + " has been already started: " + node + "!");
      }
      return;
    }

    Node parentNode = findValidParentNode(startedNodeEvent);
    if (parentNode == null) {
      return;
    }

    if (!validateNodeId(startedNodeEvent)) {
      return;
    }

    String nodeName = startedNodeEvent.getName();
    SMTestProxy childProxy = new SMTestProxy(nodeName, suite, startedNodeEvent.getLocationUrl(), true);
    TestProxyPrinterProvider printerProvider = myTestProxyPrinterProvider;
    String nodeType = startedNodeEvent.getNodeType();
    if (printerProvider != null && nodeType != null && nodeName != null) {
      Printer printer = printerProvider.getPrinterByType(nodeType, nodeName, startedNodeEvent.getNodeArgs());
      if (printer != null) {
        childProxy.setPreferredPrinter(printer);
      }
    }
    node = new Node(startedNodeEvent.getId(), parentNode, childProxy);
    myNodeByIdMap.put(startedNodeEvent.getId(), node);
    if (myLocator != null) {
      childProxy.setLocator(myLocator);
    }
    parentNode.getProxy().addChild(childProxy);
    if (startedNodeEvent.isRunning()) {
      setNodeAndAncestorsRunning(node);
    }
  }

  @Nullable
  private Node findValidParentNode(@NotNull BaseStartedNodeEvent startedNodeEvent) {
    int parentId = startedNodeEvent.getParentId();
    if (parentId < 0) {
      logProblem("Parent node id should be non-negative: " + startedNodeEvent + ".", true);
      return null;
    }
    Node parentNode = myNodeByIdMap.get(startedNodeEvent.getParentId());
    if (parentNode == null) {
      logProblem("Parent node is undefined for " + startedNodeEvent + ".", true);
      return null;
    }
    if (parentNode.getState() != State.NOT_RUNNING && parentNode.getState() != State.RUNNING) {
      logProblem("Parent node should be registered or running: " + parentNode + ", " + startedNodeEvent);
      return null;
    }
    return parentNode;
  }

  public void onTestFinished(@NotNull final TestFinishedEvent testFinishedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node node = findNodeToTerminate(testFinishedEvent);
        if (node != null) {
          SMTestProxy testProxy = node.getProxy();
          testProxy.setDuration(testFinishedEvent.getDuration());
          testProxy.setFinished();
          fireOnTestFinished(testProxy);
          terminateNode(node, State.FINISHED);
        }
      }
    });
  }

  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node node = findNodeToTerminate(suiteFinishedEvent);
        if (node != null) {
          SMTestProxy suiteProxy = node.getProxy();
          suiteProxy.setFinished();
          fireOnSuiteFinished(suiteProxy);
          terminateNode(node, State.FINISHED);
        }
      }
    });
  }

  @Nullable
  private Node findNodeToTerminate(@NotNull TreeNodeEvent treeNodeEvent) {
    Node node = findNode(treeNodeEvent);
    if (node == null) {
      logProblem("Trying to finish not existent node: " + treeNodeEvent);
      return null;
    }
    return node;
  }

  public void onUncapturedOutput(@NotNull final String text, final Key outputType) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node activeNode = findActiveNode();
        SMTestProxy activeProxy = activeNode.getProxy();
        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          activeProxy.addStdErr(text);
        } else if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
          activeProxy.addSystemOutput(text);
        } else {
          activeProxy.addStdOutput(text, outputType);
        }
      }
    });
  }

  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node activeNode = findActiveNode();
        SMTestProxy activeProxy = activeNode.getProxy();
        activeProxy.addError(localizedMessage, stackTrace, isCritical);
      }
    });
  }

  public void onCustomProgressTestsCategory(@Nullable final String categoryName,
                                            final int testCount) {
    addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestsCategory(categoryName, testCount);
      }
    });
  }

  public void onCustomProgressTestStarted() {
    addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestStarted();
      }
    });
  }

  public void onCustomProgressTestFailed() {
    addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestFailed();
      }
    });
  }

  public void onTestFailure(@NotNull final TestFailedEvent testFailedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node node = findNodeToTerminate(testFailedEvent);
        if (node == null) {
          return;
        }

        SMTestProxy testProxy = node.getProxy();

        String comparisonFailureActualText = testFailedEvent.getComparisonFailureActualText();
        String comparisonFailureExpectedText = testFailedEvent.getComparisonFailureExpectedText();
        String failureMessage = testFailedEvent.getLocalizedFailureMessage();
        String stackTrace = testFailedEvent.getStacktrace();
        if (comparisonFailureActualText != null && comparisonFailureExpectedText != null) {
          testProxy.setTestComparisonFailed(failureMessage, stackTrace,
                                            comparisonFailureActualText, comparisonFailureExpectedText);
        } else if (comparisonFailureActualText == null && comparisonFailureExpectedText == null) {
          testProxy.setTestFailed(failureMessage, stackTrace, testFailedEvent.isTestError());
        } else {
          logProblem("Comparison failure actual and expected texts should be both null or not null.\n"
                     + "Expected:\n"
                     + comparisonFailureExpectedText + "\n"
                     + "Actual:\n"
                     + comparisonFailureActualText);
        }

        // fire event
        fireOnTestFailed(testProxy);

        terminateNode(node, State.FAILED);
      }
    });
  }

  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node node = findNodeToTerminate(testIgnoredEvent);
        if (node != null) {
          SMTestProxy testProxy = node.getProxy();
          testProxy.setTestIgnored(testIgnoredEvent.getIgnoreComment(), testIgnoredEvent.getStacktrace());
          // fire event
          fireOnTestIgnored(testProxy);
          terminateNode(node, State.IGNORED);
        }
      }
    });
  }

  public void onTestOutput(@NotNull final TestOutputEvent testOutputEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node node = findNode(testOutputEvent);
        if (node == null) {
          logProblem("Test wasn't started! But " + testOutputEvent + "!");
          return;
        }
        SMTestProxy testProxy = node.getProxy();

        if (testOutputEvent.isStdOut()) {
          testProxy.addStdOutput(testOutputEvent.getText(), ProcessOutputTypes.STDOUT);
        } else {
          testProxy.addStdErr(testOutputEvent.getText());
        }
      }
    });
  }

  public void onTestsCountInSuite(final int count) {
    addToInvokeLater(new Runnable() {
      public void run() {
        fireOnTestsCountInSuite(count);
      }
    });
  }

  private boolean validateNodeId(@NotNull TreeNodeEvent treeNodeEvent) {
    int nodeId = treeNodeEvent.getId();
    if (nodeId <= 0) {
      logProblem("Node id should be positive: " + treeNodeEvent + ".", true);
      return false;
    }
    return true;
  }

  @Nullable
  private Node findNode(@NotNull TreeNodeEvent treeNodeEvent) {
    if (!validateNodeId(treeNodeEvent)) {
      return null;
    }
    return myNodeByIdMap.get(treeNodeEvent.getId());
  }

  private void fireOnTestingStarted() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestingStarted(myTestsRootProxy);
    }
  }

  private void fireOnTestingFinished() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestingFinished(myTestsRootProxy);
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestsCountInSuite(count);
    }
  }


  private void fireOnTestStarted(final SMTestProxy test) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestStarted(test);
    }
  }

  private void fireOnTestFinished(final SMTestProxy test) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestFinished(test);
    }
  }

  private void fireOnTestFailed(final SMTestProxy test) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestFailed(test);
    }
  }

  private void fireOnTestIgnored(final SMTestProxy test) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestIgnored(test);
    }
  }

  private void fireOnSuiteStarted(final SMTestProxy suite) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onSuiteStarted(suite);
    }
  }

  private void fireOnSuiteFinished(final SMTestProxy suite) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onSuiteFinished(suite);
    }
  }


  private void fireOnCustomProgressTestsCategory(@Nullable final String categoryName, int testCount) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestsCategory(categoryName, testCount);
    }
  }

  private void fireOnCustomProgressTestStarted() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestStarted();
    }
  }

  private void fireOnCustomProgressTestFailed() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestFailed();
    }
  }

  /*
   * Remove listeners,  etc
   */
  public void dispose() {
    super.dispose();
    addToInvokeLater(new Runnable() {
      public void run() {
        myEventsListeners.clear();

        if (!myRunningTestNodes.isEmpty()) {
          Application application = ApplicationManager.getApplication();
          if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            logProblem("Not all events were processed!");
          }
        }
        myRunningTestNodes.clear();
        myNodeByIdMap.clear();
      }
    });
  }

  private void setNodeAndAncestorsRunning(@NotNull Node lowestNode) {
    Node node = lowestNode;
    while (node != null && node != myTestsRootNode && node.getState() == State.NOT_RUNNING) {
      node.setState(State.RUNNING);
      SMTestProxy proxy = node.getProxy();
      proxy.setStarted();
      if (proxy.isSuite()) {
        fireOnSuiteStarted(proxy);
      } else {
        myRunningTestNodes.add(lowestNode);
        fireOnTestStarted(proxy);
      }
      node = node.getParentNode();
    }
  }

  private void terminateNode(@NotNull Node node, @NotNull State terminateState) {
    node.setState(terminateState);
    myRunningTestNodes.remove(node);
  }

  @NotNull
  private Node findActiveNode() {
    if (myRunningTestNodes.isEmpty()) {
      return myTestsRootNode;
    }
    return myRunningTestNodes.iterator().next();
  }

  private void logProblem(@NotNull String msg) {
    logProblem(msg, SMTestRunnerConnectionUtil.isInDebugMode());
  }

  private void logProblem(@NotNull String msg, boolean throwError) {
    final String text = "[" + myTestFrameworkName + "] " + msg;
    if (throwError) {
      LOG.error(text);
    }
    else {
      LOG.warn(text);
    }
  }

  private enum State {
    NOT_RUNNING, RUNNING, FINISHED, FAILED, IGNORED
  }

  private static class Node {
    private final int myId;
    private final Node myParentNode;
    private final SMTestProxy myProxy;
    private State myState;

    Node(int id, @Nullable Node parentNode, @NotNull SMTestProxy proxy) {
      myId = id;
      myParentNode = parentNode;
      myProxy = proxy;
      myState = State.NOT_RUNNING;
    }

    public int getId() {
      return myId;
    }

    @Nullable
    public Node getParentNode() {
      return myParentNode;
    }

    @NotNull
    public SMTestProxy getProxy() {
      return myProxy;
    }

    @NotNull
    public State getState() {
      return myState;
    }

    public void setState(@NotNull State newState) {
      boolean accepted = false;
      if (myState == State.NOT_RUNNING || myState == State.RUNNING) {
        accepted = myState.ordinal() < newState.ordinal();
      }
      if (!accepted) {
        throw new RuntimeException("Illegal state change [" + myState + " -> " + newState + "]: " + toString());
      }
      myState = newState;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Node node = (Node)o;

      return myId == node.myId;
    }

    @Override
    public int hashCode() {
      return myId;
    }

    @Override
    public String toString() {
      return "{" +
             "id=" + myId +
             ", parentId=" + (myParentNode != null ? String.valueOf(myParentNode.getId()) : "<undefined>") +
             ", name='" + myProxy.getName() +
             "', isSuite=" + myProxy.isSuite() +
             ", state=" + myState +
             '}';
    }
  }

}
