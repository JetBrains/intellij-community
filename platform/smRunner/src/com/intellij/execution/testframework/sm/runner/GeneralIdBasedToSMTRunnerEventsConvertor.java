// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.Printer;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class GeneralIdBasedToSMTRunnerEventsConvertor extends GeneralTestEventsProcessor {

  private static final Logger LOG = Logger.getInstance(GeneralIdBasedToSMTRunnerEventsConvertor.class);

  private final HashMap<String, Node> myNodeByIdMap = new HashMap<>();
  private final Set<Node> myRunningTestNodes = ContainerUtil.newHashSet();
  private final Set<Node> myRunningSuiteNodes = ContainerUtil.newHashSet();
  private final Node myTestsRootNode;

  private boolean myIsTestingFinished = false;
  private TestProxyPrinterProvider myTestProxyPrinterProvider = null;

  public GeneralIdBasedToSMTRunnerEventsConvertor(Project project,
                                                  @NotNull SMTestProxy.SMRootTestProxy testsRootProxy,
                                                  @NotNull String testFrameworkName) {
    super(project, testFrameworkName, testsRootProxy);
    myTestsRootNode = new Node(TreeNodeEvent.ROOT_NODE_ID, null, testsRootProxy);
    myNodeByIdMap.put(myTestsRootNode.getId(), myTestsRootNode);
  }

  @Override
  public void onStartTesting() {
    LOG.debug("onStartTesting");
    addToInvokeLater(() -> {
      LOG.debug("onStartTesting: invoked");
      myTestsRootNode.setState(State.RUNNING, this);
      myTestsRootProxy.setStarted();
      fireOnTestingStarted(myTestsRootProxy);
    });
  }

  @Override
  public void onTestsReporterAttached() {
    addToInvokeLater(() -> fireOnTestsReporterAttached(myTestsRootProxy));
  }

  @Override
  public void onFinishTesting() {
    LOG.debug("onFinishTesting");
    addToInvokeLater(() -> {
      LOG.debug("onFinishTesting: invoked");
      if (myIsTestingFinished) {
        LOG.debug("has already been invoked");
        // has been already invoked!
        return;
      }
      myIsTestingFinished = true;

      // We don't know whether process was destroyed by user
      // or it finished after all tests have been run
      // Lets assume, if at finish all nodes except root suite have final state (passed, failed or ignored),
      // then all is ok otherwise process was terminated by user
      boolean completeTree = isTreeComplete(myRunningTestNodes, myTestsRootProxy);
      if (LOG.isDebugEnabled()) {
        LOG.debug("completeTree:" + completeTree);
      }
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
      myRunningSuiteNodes.clear();

      fireOnTestingFinished(myTestsRootProxy);
    });
    super.onFinishTesting();
  }

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
    myTestProxyPrinterProvider = printerProvider;
  }

  @Override
  public void onTestStarted(@NotNull final TestStartedEvent testStartedEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("onTestStarted " + testStartedEvent.getId());
    }
    addToInvokeLater(() -> doStartNode(testStartedEvent, false));
  }

  @Override
  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("onSuiteStarted " + suiteStartedEvent.getId());
    }
    addToInvokeLater(() -> doStartNode(suiteStartedEvent, true));
  }

  private void doStartNode(@NotNull BaseStartedNodeEvent startedNodeEvent, boolean suite) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("doStartNode " + startedNodeEvent.getId());
    }
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

    node = createNode(startedNodeEvent, suite);
    if (node == null) return;
    if (startedNodeEvent.isRunning()) {
      setNodeAndAncestorsRunning(node);
    }
  }

  private Node createNode(@NotNull BaseStartedNodeEvent startedNodeEvent, boolean suite) {
    Node parentNode = findValidParentNode(startedNodeEvent);
    if (parentNode == null) {
      return null;
    }

    String nodeId = validateAndGetNodeId(startedNodeEvent);
    if (nodeId == null) {
      return null;
    }

    String nodeName = startedNodeEvent.getName();
    SMTestProxy childProxy = new SMTestProxy(nodeName, suite, startedNodeEvent.getLocationUrl(), startedNodeEvent.getMetainfo(), true);
    childProxy.putUserData(SMTestProxy.NODE_ID, startedNodeEvent.getId());
    childProxy.setTreeBuildBeforeStart();
    TestProxyPrinterProvider printerProvider = myTestProxyPrinterProvider;
    String nodeType = startedNodeEvent.getNodeType();
    if (printerProvider != null && nodeType != null && nodeName != null) {
      Printer printer = printerProvider.getPrinterByType(nodeType, nodeName, startedNodeEvent.getNodeArgs());
      if (printer != null) {
        childProxy.setPreferredPrinter(printer);
      }
    }

    Node node = new Node(nodeId, parentNode, childProxy);
    myNodeByIdMap.put(startedNodeEvent.getId(), node);
    if (myLocator != null) {
      childProxy.setLocator(myLocator);
    }
    parentNode.getProxy().addChild(childProxy);
    return node;
  }

  @Override
  protected SMTestProxy createSuite(String suiteName, String locationHint, String metaInfo, String id, String parentNodeId) {
    Node node = createNode(new TestSuiteStartedEvent(suiteName, id, parentNodeId, locationHint, metaInfo, null, null, false), true);
    return node.getProxy();
  }

  @Override
  protected SMTestProxy createProxy(String testName, String locationHint, String metaInfo, String id, String parentNodeId) {
    Node node = createNode(new TestStartedEvent(testName, id, parentNodeId, locationHint, metaInfo, null, null, false), false);
    return node.getProxy();
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

  @Override
  public void onTestFinished(@NotNull final TestFinishedEvent testFinishedEvent) {
    LOG.debug("onTestFinished");
    addToInvokeLater(() -> {
      LOG.debug("onTestFinished: invoked");
      Node node = findNodeToTerminate(testFinishedEvent);
      if (node != null) {
        SMTestProxy testProxy = node.getProxy();
        final Long duration = testFinishedEvent.getDuration();
        if (duration != null) {
          testProxy.setDuration(duration);
        }
        testProxy.setFrameworkOutputFile(testFinishedEvent.getOutputFile());
        testProxy.setFinished();
        fireOnTestFinishedIfNeeded(testProxy, node);
        terminateNode(node, State.FINISHED);
      }
    });
  }

  private void fireOnTestFinishedIfNeeded(@NotNull SMTestProxy testProxy, @NotNull Node node) {
    // allow clients to omit sending 'testFinished' messages after 'testFailed'/'testIgnored' messages
    if (node.getState() != State.FINISHED && node.getState() != State.FAILED && node.getState() != State.IGNORED) {
      LOG.debug("onTestFinished: state != FINISHED && state != FAILED && state != IGNORED");
      // Don't count the same test twice if 'testFailed' or 'testIgnored' message is followed by 'testFinished' message
      // which may happen if generated TeamCity messages adhere rules from
      //   https://confluence.jetbrains.com/display/TCD10/Build+Script+Interaction+with+TeamCity
      // Anyway, this id-based converter already breaks TeamCity protocol by expecting messages with
      // non-standard TeamCity attributes: 'nodeId'/'parentNodeId' instead of 'name'.
      fireOnTestFinished(testProxy);
    }
  }

  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("onSuiteFinished " + suiteFinishedEvent.getId());
    }
    addToInvokeLater(() -> {
      if (LOG.isDebugEnabled()) {
        LOG.debug("onSuiteFinished invoked " + suiteFinishedEvent.getId());
      }
      Node node = findNodeToTerminate(suiteFinishedEvent);
      if (node != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("finished:" + node.myId);
        }
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
      logProblem("Trying to finish nonexistent node: " + treeNodeEvent);
      return null;
    }
    return node;
  }

  @Override
  public void onUncapturedOutput(@NotNull final String text, final Key outputType) {
    LOG.debug("onUncapturedOutput " + text);
    addToInvokeLater(() -> {
      Node activeNode = findActiveNode();
      SMTestProxy activeProxy = activeNode.getProxy();
      activeProxy.addOutput(text, outputType);
    });
  }

  @Override
  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    onError(null, localizedMessage, stackTrace, isCritical);
  }

  public void onError(@Nullable final String nodeId,
                      @NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    LOG.debug("onError " + localizedMessage);
    addToInvokeLater(() -> {
      if (LOG.isDebugEnabled()) {
        LOG.debug("onError invoked " + localizedMessage);
      }
      SMTestProxy activeProxy = null;
      if (nodeId != null) {
        activeProxy = findProxyById(nodeId);
      }
      if (activeProxy == null) {
        Node activeNode = findActiveNode();
        activeProxy = activeNode.getProxy();
      }
      activeProxy.addError(localizedMessage, stackTrace, isCritical);
    });
  }
  
  @Override
  public void onTestFailure(@NotNull final TestFailedEvent testFailedEvent) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("onTestFailure " + testFailedEvent.getId());
    }
    addToInvokeLater(() -> {
      if (LOG.isDebugEnabled()) {
        LOG.debug("onTestFailure invoked " + testFailedEvent.getId());
      }
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
        testProxy.setTestComparisonFailed(failureMessage, stackTrace, comparisonFailureActualText, comparisonFailureExpectedText, testFailedEvent);
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

      fireOnTestFailed(testProxy);
      fireOnTestFinishedIfNeeded(testProxy, node);

      terminateNode(node, State.FAILED);
    });
  }

  @Override
  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
    LOG.debug("onTestIgnored");
    addToInvokeLater(() -> {
      LOG.debug("onTestIgnored invoked");
      Node node = findNodeToTerminate(testIgnoredEvent);
      if (node != null) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("onTestIgnored node " + node.myId);
        }
        SMTestProxy testProxy = node.getProxy();
        testProxy.setTestIgnored(testIgnoredEvent.getIgnoreComment(), testIgnoredEvent.getStacktrace());

        fireOnTestIgnored(testProxy);
        fireOnTestFinishedIfNeeded(testProxy, node);

        terminateNode(node, State.IGNORED);
      }
    });
  }

  @Override
  public void onTestOutput(@NotNull final TestOutputEvent testOutputEvent) {
    LOG.debug("onTestOutput");
    addToInvokeLater(() -> {
      LOG.debug("onTestOutput invoke");
      Node node = findNode(testOutputEvent);
      if (node == null) {
        logProblem("Test wasn't started! But " + testOutputEvent + "!");
        return;
      }
      node.getProxy().addOutput(testOutputEvent.getText(), testOutputEvent.getOutputType());
    });
  }

  @Override
  public void onTestsCountInSuite(final int count) {
    LOG.debug("onTestsCountInSuite");
    addToInvokeLater(() -> fireOnTestsCountInSuite(count));
  }

  @Nullable
  private String validateAndGetNodeId(@NotNull TreeNodeEvent treeNodeEvent) {
    String nodeId = treeNodeEvent.getId();
    if (nodeId == null || nodeId.equals(TreeNodeEvent.ROOT_NODE_ID)) {
      logProblem((nodeId == null ? "Missing" : "Illegal") + " nodeId: " + treeNodeEvent, true);
    }
    return nodeId;
  }

  @Nullable
  private Node findNode(@NotNull TreeNodeEvent treeNodeEvent) {
    String nodeId = validateAndGetNodeId(treeNodeEvent);
    return nodeId != null ? myNodeByIdMap.get(nodeId) : null;
  }

  @Nullable
  public SMTestProxy findProxyById(@NotNull String id) {
    Node node = myNodeByIdMap.get(id);
    return node != null ? node.getProxy() : null;
  }
  
  /*
   * Remove listeners,  etc
   */
  @Override
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
      myRunningSuiteNodes.clear();
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
        myRunningSuiteNodes.add(node);
        fireOnSuiteStarted(proxy);
      } else {
        myRunningTestNodes.add(lowestNode);
        fireOnTestStarted(proxy);
      }
      node = node.getParentNode();
    }
  }

  private void terminateNode(@NotNull Node node, @NotNull State terminateState) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("terminateNode " + node.myId);
    }
    node.setState(terminateState, this);
    myRunningTestNodes.remove(node);
    myRunningSuiteNodes.remove(node);
  }

  @NotNull
  private Node findActiveNode() {
    if (!myRunningTestNodes.isEmpty()) {
      return myRunningTestNodes.iterator().next();
    }
    if (!myRunningSuiteNodes.isEmpty()) {
      return myRunningSuiteNodes.iterator().next();
    }
    return myTestsRootNode;
  }

  private enum State {
    NOT_RUNNING, RUNNING, FINISHED, FAILED, IGNORED
  }

  private static class Node {
    private final String myId;
    private final Node myParentNode;
    private final SMTestProxy myProxy;
    private State myState;

    Node(@NotNull String id, @Nullable Node parentNode, @NotNull SMTestProxy proxy) {
      myId = id;
      myParentNode = parentNode;
      myProxy = proxy;
      myState = State.NOT_RUNNING;
    }

    @NotNull
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
      return myId.hashCode();
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
