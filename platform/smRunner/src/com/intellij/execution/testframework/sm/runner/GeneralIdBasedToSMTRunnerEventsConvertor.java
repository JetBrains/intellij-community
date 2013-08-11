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
  private final Set<Node> myRunningNodes = ContainerUtil.newHashSet();
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
    myRunningNodes.add(myTestsRootNode);
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
        // Lets assume, if at finish all suites except root suite are passed
        // then all is ok otherwise process was terminated by user
        if (myRunningNodes.size() == 1 && myRunningNodes.contains(myTestsRootNode)) {
          myTestsRootProxy.setFinished();
        } else {
          logProblem("Unexpected running nodes: " + myRunningNodes);
          myTestsRootProxy.setTerminated();
        }
        myNodeByIdMap.clear();
        myRunningNodes.clear();

        fireOnTestingFinished();
      }
    });
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
    Node parentNode = findValidParentNode(startedNodeEvent);
    if (parentNode == null) {
      return;
    }

    if (!validateNodeId(startedNodeEvent)) {
      return;
    }
    int nodeId = startedNodeEvent.getId();
    Node childNode = myNodeByIdMap.get(nodeId);
    if (childNode != null) {
      logProblem(startedNodeEvent + " has been already started: " + childNode + "!");
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
    childNode = new Node(startedNodeEvent.getId(), parentNode, childProxy);
    myNodeByIdMap.put(nodeId, childNode);
    myRunningNodes.add(childNode);
    if (myLocator != null) {
      childProxy.setLocator(myLocator);
    }
    parentNode.getProxy().addChild(childProxy);

    // progress started
    childProxy.setStarted();
    if (suite) {
      fireOnSuiteStarted(childProxy);
    } else {
      fireOnTestStarted(childProxy);
    }
  }

  @Nullable
  private Node findValidParentNode(@NotNull BaseStartedNodeEvent startedNodeEvent) {
    int parentId = startedNodeEvent.getParentId();
    if (parentId < 0) {
      logProblem("Parent node id should be non-negative: " + startedNodeEvent + ".");
      return null;
    }
    Node parentNode = myNodeByIdMap.get(startedNodeEvent.getParentId());
    if (parentNode == null) {
      logProblem("Parent node is undefined for " + startedNodeEvent + ".");
      return null;
    }
    if (parentNode.getState() != State.RUNNING) {
      logProblem("Parent node should be running: " + parentNode + ", " + startedNodeEvent);
      return null;
    }
    return parentNode;
  }

  public void onTestFinished(@NotNull final TestFinishedEvent testFinishedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        SMTestProxy testProxy = getProxyToFinish(testFinishedEvent);
        if (testProxy != null) {
          testProxy.setDuration(testFinishedEvent.getDuration());
          testProxy.setFinished();
          fireOnTestFinished(testProxy);
        }
      }
    });
  }

  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        SMTestProxy suiteProxy = getProxyToFinish(suiteFinishedEvent);
        if (suiteProxy != null) {
          suiteProxy.setFinished();
          fireOnSuiteFinished(suiteProxy);
        }
      }
    });
  }

  @Nullable
  private SMTestProxy getProxyToFinish(@NotNull TreeNodeEvent treeNodeEvent) {
    Node finishedNode = findNode(treeNodeEvent);
    if (finishedNode == null) {
      logProblem("Trying to finish not started node: " + treeNodeEvent);
      return null;
    }
    stopRunningNode(finishedNode, State.FINISHED, treeNodeEvent);
    return finishedNode.getProxy();
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
        Node node = findNode(testFailedEvent);
        if (node == null) {
          logProblem("Test wasn't started! " + testFailedEvent + ".");
          return;
        }
        stopRunningNode(node, State.FAILED, testFailedEvent);

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
      }
    });
  }

  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        Node node = findNode(testIgnoredEvent);
        if (node == null) {
          logProblem("Test wasn't started! " + testIgnoredEvent + ".");
          return;
        }
        stopRunningNode(node, State.IGNORED, testIgnoredEvent);

        SMTestProxy testProxy = node.getProxy();
        testProxy.setTestIgnored(testIgnoredEvent.getIgnoreComment(), testIgnoredEvent.getStacktrace());

        // fire event
        fireOnTestIgnored(testProxy);
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
      logProblem("Node id should be positive: " + treeNodeEvent + ".");
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

        if (!myRunningNodes.isEmpty()) {
          Application application = ApplicationManager.getApplication();
          if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            logProblem("Not all events were processed!");
          }
        }
        myRunningNodes.clear();
        myNodeByIdMap.clear();
      }
    });
  }

  private void stopRunningNode(@NotNull Node node, @NotNull State stoppedState, @NotNull TreeNodeEvent event) {
    if (stoppedState == State.RUNNING) {
      throw new RuntimeException("newState shouldn't be " + State.RUNNING);
    }
    // check if has been already processed
    if (node.getState() != State.RUNNING) {
      logProblem("Can't change state of already stopped node" + node + " to " + stoppedState + ", " + event + ".");
      return;
    }
    myRunningNodes.remove(node);
    node.setState(stoppedState);
  }

  @NotNull
  private Node findActiveNode() {
    List<Node> runningLeaves = ContainerUtil.newArrayListWithExpectedSize(1);
    for (Node node : myRunningNodes) {
      if (!node.hasRunningChildren()) {
        runningLeaves.add(node);
      }
    }
    if (runningLeaves.isEmpty()) {
      throw new RuntimeException("No running leaves found, running nodes: " + myRunningNodes);
    }
    if (runningLeaves.size() == 1) {
      return runningLeaves.iterator().next();
    }
    List<Node> commonPathToRoot = null;
    for (Node leaf : runningLeaves) {
      List<Node> pathToRoot = leaf.getAncestorsFromParentToRoot();
      if (commonPathToRoot == null) {
        commonPathToRoot = pathToRoot;
      } else {
        commonPathToRoot = intersectPathsToRoot(commonPathToRoot, pathToRoot);
      }
    }
    if (commonPathToRoot == null || commonPathToRoot.isEmpty()) {
      throw new RuntimeException("Unexpected common path to root: " + commonPathToRoot + ", running leaves: " + runningLeaves);
    }
    return commonPathToRoot.get(0);
  }

  @NotNull
  private static List<Node> intersectPathsToRoot(@NotNull List<Node> pathToRoot1, @NotNull List<Node> pathToRoot2) {
    final int minSize = Math.min(pathToRoot1.size(), pathToRoot2.size());
    final int shift1 = pathToRoot1.size() - minSize;
    final int shift2 = pathToRoot2.size() - minSize;
    int commonSize = 0;
    for (int i = 0; i < minSize; i++) {
      Node node1 = pathToRoot1.get(i + shift1);
      Node node2 = pathToRoot2.get(i + shift2);
      if (node1 == node2) {
        commonSize = minSize - i;
        break;
      }
    }
    return pathToRoot1.subList(pathToRoot1.size() - commonSize, pathToRoot1.size());
  }

  private static String getTestFrameworkPrefix(@NotNull String testFrameworkName) {
    return "[" + testFrameworkName + "] ";
  }

  private void logProblem(@NotNull String msg) {
    logProblem(LOG, msg, myTestFrameworkName);
  }

  private static void logProblem(@NotNull Logger log, @NotNull String msg, @NotNull String testFrameworkName) {
    logProblem(log, msg, SMTestRunnerConnectionUtil.isInDebugMode(), testFrameworkName);
  }

  private static void logProblem(@NotNull Logger log, @NotNull String msg, boolean throwError, @NotNull String testFrameworkName) {
    final String text = getTestFrameworkPrefix(testFrameworkName) + msg;
    if (throwError) {
      log.error(text);
    }
    else {
      log.warn(text);
    }
  }

  private enum State {
    RUNNING, FINISHED, FAILED, IGNORED
  }

  private static class Node {
    private final int myId;
    private final Node myParentNode;
    private final SMTestProxy myProxy;
    private State myState = State.RUNNING;
    private int myRunningChildCount = 0;

    Node(int id, @Nullable Node parentNode, @NotNull SMTestProxy proxy) {
      myId = id;
      myParentNode = parentNode;
      myProxy = proxy;
      if (myParentNode != null) {
        myParentNode.myRunningChildCount++;
      }
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

    public void setState(@NotNull State state) {
      if (myState == State.RUNNING && state != State.RUNNING) {
        if (myParentNode != null) {
          myParentNode.myRunningChildCount--;
        }
      } else {
        throw new RuntimeException("Attempt to change state from " + myState + " to " + state + ":" + toString());
      }
      myState = state;
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

    public boolean hasRunningChildren() {
      return myRunningChildCount > 0;
    }

    @NotNull
    public List<Node> getAncestorsFromParentToRoot() {
      List<Node> ancestors = ContainerUtil.newArrayList();
      Node parent = getParentNode();
      while (parent != null) {
        ancestors.add(parent);
        parent = parent.getParentNode();
      }
      return ancestors;
    }
  }
}
