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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class GeneralIdBasedToSMTRunnerEventsConvertor extends GeneralTestEventsProcessor {

  private final HashMap<String, Node> myNodeByIdMap = new HashMap<>();
  private final Set<Node> myRunningTestNodes = ContainerUtil.newHashSet();
  private final SMTestProxy.SMRootTestProxy myTestsRootProxy;
  private final Node myTestsRootNode;

  private boolean myIsTestingFinished = false;
  private SMTestLocator myLocator = null;
  private TestProxyPrinterProvider myTestProxyPrinterProvider = null;

  public GeneralIdBasedToSMTRunnerEventsConvertor(Project project,
                                                  @NotNull SMTestProxy.SMRootTestProxy testsRootProxy,
                                                  @NotNull String testFrameworkName) {
    super(project, testFrameworkName);
    myTestsRootProxy = testsRootProxy;
    myTestsRootNode = new Node(TreeNodeEvent.ROOT_NODE_ID, null, testsRootProxy);
    myNodeByIdMap.put(myTestsRootNode.getId(), myTestsRootNode);
  }

  @Override
  public void setLocator(@NotNull SMTestLocator locator) {
    myLocator = locator;
  }

  public void onStartTesting() {
    addToInvokeLater(() -> {
      myTestsRootNode.setState(State.RUNNING, this);
      myTestsRootProxy.setStarted();
      fireOnTestingStarted(myTestsRootProxy);
    });
  }

  @Override
  public void onTestsReporterAttached() {
    addToInvokeLater(() -> fireOnTestsReporterAttached(myTestsRootProxy));
  }

  public void onFinishTesting() {
    addToInvokeLater(() -> {
      if (myIsTestingFinished) {
        // has been already invoked!
        return;
      }
      myIsTestingFinished = true;

      // We don't know whether process was destroyed by user
      // or it finished after all tests have been run
      // Lets assume, if at finish all nodes except root suite have final state (passed, failed or ignored),
      // then all is ok otherwise process was terminated by user
      boolean completeTree = isTreeComplete(myRunningTestNodes, myTestsRootProxy);
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

      fireOnTestingFinished(myTestsRootProxy);
    });
    stopEventProcessing();
  }

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
    myTestProxyPrinterProvider = printerProvider;
  }

  public void onTestStarted(@NotNull final TestStartedEvent testStartedEvent) {
    addToInvokeLater(() -> doStartNode(testStartedEvent, false));
  }

  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    addToInvokeLater(() -> doStartNode(suiteStartedEvent, true));
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
    String parentId = startedNodeEvent.getParentId();
    if (parentId == null) {
      logProblem("Parent node id should be defined: " + startedNodeEvent + ".", true);
      return null;
    }
    Node parentNode = myNodeByIdMap.get(parentId);
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
    addToInvokeLater(() -> {
      Node node = findNodeToTerminate(testFinishedEvent);
      if (node != null) {
        SMTestProxy testProxy = node.getProxy();
        testProxy.setDuration(testFinishedEvent.getDuration());
        testProxy.setFrameworkOutputFile(testFinishedEvent.getOutputFile());
        testProxy.setFinished();
        fireOnTestFinished(testProxy);
        terminateNode(node, State.FINISHED);
      }
    });
  }

  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    addToInvokeLater(() -> {
      Node node = findNodeToTerminate(suiteFinishedEvent);
      if (node != null) {
        SMTestProxy suiteProxy = node.getProxy();
        suiteProxy.setFinished();
        fireOnSuiteFinished(suiteProxy);
        terminateNode(node, State.FINISHED);
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
    addToInvokeLater(() -> {
      Node activeNode = findActiveNode();
      SMTestProxy activeProxy = activeNode.getProxy();
      if (ProcessOutputTypes.STDERR.equals(outputType)) {
        activeProxy.addStdErr(text);
      } else if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
        activeProxy.addSystemOutput(text);
      } else {
        activeProxy.addStdOutput(text, outputType);
      }
    });
  }

  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    addToInvokeLater(() -> {
      Node activeNode = findActiveNode();
      SMTestProxy activeProxy = activeNode.getProxy();
      activeProxy.addError(localizedMessage, stackTrace, isCritical);
    });
  }

  public void onTestFailure(@NotNull final TestFailedEvent testFailedEvent) {
    addToInvokeLater(() -> {
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
                                          comparisonFailureActualText, comparisonFailureExpectedText,
                                          testFailedEvent.getExpectedFilePath(),
                                          testFailedEvent.getActualFilePath());
      } else if (comparisonFailureActualText == null && comparisonFailureExpectedText == null) {
        testProxy.setTestFailed(failureMessage, stackTrace, testFailedEvent.isTestError());
      } else {
        logProblem("Comparison failure actual and expected texts should be both null or not null.\n"
                   + "Expected:\n"
                   + comparisonFailureExpectedText + "\n"
                   + "Actual:\n"
                   + comparisonFailureActualText);
      }
      long duration = testFailedEvent.getDurationMillis();
      if (duration >= 0) {
        testProxy.setDuration(duration);
      }

      // fire event
      fireOnTestFailed(testProxy);

      terminateNode(node, State.FAILED);
    });
  }

  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
    addToInvokeLater(() -> {
      Node node = findNodeToTerminate(testIgnoredEvent);
      if (node != null) {
        SMTestProxy testProxy = node.getProxy();
        testProxy.setTestIgnored(testIgnoredEvent.getIgnoreComment(), testIgnoredEvent.getStacktrace());
        // fire event
        fireOnTestIgnored(testProxy);
        terminateNode(node, State.IGNORED);
      }
    });
  }

  public void onTestOutput(@NotNull final TestOutputEvent testOutputEvent) {
    addToInvokeLater(() -> {
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
    });
  }

  public void onTestsCountInSuite(final int count) {
    addToInvokeLater(() -> fireOnTestsCountInSuite(count));
  }

  private boolean validateNodeId(@NotNull TreeNodeEvent treeNodeEvent) {
    String nodeId = treeNodeEvent.getId();
    if (nodeId == null || nodeId.equals(TreeNodeEvent.ROOT_NODE_ID)) {
      logProblem("Node id should be initialized: " + treeNodeEvent + ".", true);
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

  @Nullable
  public SMTestProxy findProxyById(@NotNull String id) {
    Node node = myNodeByIdMap.get(id);
    return node != null ? node.getProxy() : null;
  }
  
  /*
   * Remove listeners,  etc
   */
  public void dispose() {
    super.dispose();
    addToInvokeLater(() -> {
      disconnectListeners();

      if (!myRunningTestNodes.isEmpty()) {
        Application application = ApplicationManager.getApplication();
        if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
          logProblem("Not all events were processed!");
        }
      }
      myRunningTestNodes.clear();
      myNodeByIdMap.clear();
    });
  }

  private void setNodeAndAncestorsRunning(@NotNull Node lowestNode) {
    Node node = lowestNode;
    while (node != null && node != myTestsRootNode && node.getState() == State.NOT_RUNNING) {
      node.setState(State.RUNNING, this);
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
    node.setState(terminateState, this);
    myRunningTestNodes.remove(node);
  }

  @NotNull
  private Node findActiveNode() {
    if (myRunningTestNodes.isEmpty()) {
      return myTestsRootNode;
    }
    return myRunningTestNodes.iterator().next();
  }

  private enum State {
    NOT_RUNNING, RUNNING, FINISHED, FAILED, IGNORED
  }

  private static class Node {
    private final String myId;
    private final Node myParentNode;
    private final SMTestProxy myProxy;
    private State myState;

    Node(@Nullable String id, @Nullable Node parentNode, @NotNull SMTestProxy proxy) {
      myId = id;
      myParentNode = parentNode;
      myProxy = proxy;
      myState = State.NOT_RUNNING;
    }

    @Nullable
    public String getId() {
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

    public void setState(@NotNull State newState, @NotNull GeneralIdBasedToSMTRunnerEventsConvertor convertor) {
      // allowed sequences: NOT_RUNNING -> RUNNING or IGNORED; RUNNING -> FINISHED, FAILED or IGNORED; FINISHED <-> FAILED; IGNORED -> FINISHED
      if (myState == State.NOT_RUNNING && newState != State.RUNNING && newState != State.IGNORED ||
          myState == State.RUNNING && newState != State.FINISHED && newState != State.FAILED && newState != State.IGNORED ||
          myState == State.FINISHED && newState != State.FAILED ||
          myState == State.FAILED && newState != State.FINISHED ||
          myState == State.IGNORED && newState != State.FINISHED) {
        convertor.logProblem("Illegal state change [" + myState + " -> " + newState + "]: " + toString(), false);
      }

      if (myState.ordinal() < newState.ordinal()) {
        // for example State.FINISHED comes later than State.FAILED, do not update state in this case
        myState = newState;
      }
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
      return myId != null ? myId.hashCode() : -1;
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
