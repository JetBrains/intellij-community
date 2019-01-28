// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * This class fires events to SMTRunnerEventsListener in event dispatch thread.
 *
 * @author: Roman Chernyatchik
 */
public class GeneralToSMTRunnerEventsConvertor extends GeneralTestEventsProcessor {

  private final Map<String, SMTestProxy> myRunningTestsFullNameToProxy = ContainerUtil.newConcurrentMap();
  private final TestSuiteStack mySuitesStack;
  private final Map<String, List<SMTestProxy>> myCurrentChildren = new HashMap<>();

  private boolean myIsTestingFinished;


  public GeneralToSMTRunnerEventsConvertor(Project project, @NotNull SMTestProxy.SMRootTestProxy testsRootNode,
                                           @NotNull String testFrameworkName) {
    super(project, testFrameworkName, testsRootNode);
    mySuitesStack = new TestSuiteStack(testFrameworkName);
  }

  @Override
  protected SMTestProxy createProxy(String testName, String locationHint, String metaInfo, String id, String parentNodeId) {
    SMTestProxy proxy = super.createProxy(testName, locationHint, metaInfo, id, parentNodeId);
    SMTestProxy currentSuite = getCurrentSuite();
    currentSuite.addChild(proxy);
    return proxy;
  }

  @Override
  protected SMTestProxy createSuite(String suiteName, String locationHint, String metaInfo, String id, String parentNodeId) {
    SMTestProxy newSuite = super.createSuite(suiteName, locationHint, metaInfo, id, parentNodeId);
    final SMTestProxy parentSuite = getCurrentSuite();

    parentSuite.addChild(newSuite);

    mySuitesStack.pushSuite(newSuite);

    return newSuite;
  }

  @Override
  public void onSuiteTreeEnded(String suiteName) {
    myBuildTreeRunnables.add(() -> mySuitesStack.popSuite(suiteName));
    super.onSuiteTreeEnded(suiteName);
  }

  @Override
  public void onStartTesting() {
    //fire
    mySuitesStack.pushSuite(myTestsRootProxy);
    myTestsRootProxy.setStarted();

    //fire
    fireOnTestingStarted(myTestsRootProxy);
  }

  @Override
  public void onTestsReporterAttached() {
    fireOnTestsReporterAttached(myTestsRootProxy);
  }

  @Override
  public void onFinishTesting() {
    fireOnBeforeTestingFinished(myTestsRootProxy);
    // has been already invoked!
    // We don't know whether process was destroyed by user
    // or it finished after all tests have been run
    // Lets assume, if at finish all suites except root suite are passed
    // then all is ok otherwise process was terminated by user
    if (myIsTestingFinished) {
      // has been already invoked!
      return;
    }
    myIsTestingFinished = true;

    // We don't know whether process was destroyed by user
    // or it finished after all tests have been run
    // Lets assume, if at finish all suites except root suite are passed
    // then all is ok otherwise process was terminated by user
    if (!isTreeComplete(myRunningTestsFullNameToProxy.keySet(), myTestsRootProxy)) {
      myTestsRootProxy.setTerminated();
      myRunningTestsFullNameToProxy.clear();
    }
    mySuitesStack.clear();
    myTestsRootProxy.setFinished();
    myCurrentChildren.clear();


    //fire events
    fireOnTestingFinished(myTestsRootProxy);

    super.onFinishTesting();
  }

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
  }

  @Override
  public void onTestStarted(@NotNull final TestStartedEvent testStartedEvent) {
    //Duplicated event
    // creates test
    // adds to running tests map
    //Progress started
    //fire events
    final String testName = testStartedEvent.getName();
    final String locationUrl = testStartedEvent.getLocationUrl();
    final boolean isConfig = testStartedEvent.isConfig();
    final String fullName = getFullTestName(testName);

    if (myRunningTestsFullNameToProxy.containsKey(fullName)) {
      //Duplicated event
      logProblem("Test [" + fullName + "] has been already started");
      if (SMTestRunnerConnectionUtil.isInDebugMode()) {
        return;
      }
    }

    SMTestProxy parentSuite = getCurrentSuite();
    SMTestProxy testProxy = findChild(parentSuite, locationUrl != null ? locationUrl : fullName, false);
    if (testProxy == null) {
      // creates test
      testProxy = new SMTestProxy(testName, false, locationUrl, testStartedEvent.getMetainfo(), false);
      testProxy.setConfig(isConfig);
      if (myTreeBuildBeforeStart) testProxy.setTreeBuildBeforeStart();

      if (myLocator != null) {
        testProxy.setLocator(myLocator);
      }

      parentSuite.addChild(testProxy);
    }

    // adds to running tests map
    myRunningTestsFullNameToProxy.put(fullName, testProxy);

    //Progress started
    testProxy.setStarted();

    //fire events
    fireOnTestStarted(testProxy);
  }

  @Override
  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    //new suite
    //Progress started
    //fire event
    final String suiteName = suiteStartedEvent.getName();
    final String locationUrl = suiteStartedEvent.getLocationUrl();

    SMTestProxy parentSuite = getCurrentSuite();
    SMTestProxy newSuite = findChild(parentSuite, locationUrl != null ? locationUrl : suiteName, true);
    if (newSuite == null) {
      //new suite
      newSuite = new SMTestProxy(suiteName, true, locationUrl, suiteStartedEvent.getMetainfo(), parentSuite.isPreservePresentableName());
      if (myTreeBuildBeforeStart) {
        newSuite.setTreeBuildBeforeStart();
      }

      if (myLocator != null) {
        newSuite.setLocator(myLocator);
      }

      parentSuite.addChild(newSuite);
    }

    initCurrentChildren(newSuite, true);
    mySuitesStack.pushSuite(newSuite);

    //Progress started
    newSuite.setSuiteStarted();

    //fire event
    fireOnSuiteStarted(newSuite);
  }

  private void initCurrentChildren(SMTestProxy newSuite, boolean preferSuite) {
    if (myTreeBuildBeforeStart) {
      for (SMTestProxy proxy : newSuite.getChildren()) {
        if (!proxy.isFinal() || preferSuite && proxy.isSuite()) {
          String url = proxy.getLocationUrl();
          if (url != null) {
            myCurrentChildren.computeIfAbsent(url, l -> new ArrayList<>()).add(proxy);
          }
          myCurrentChildren.computeIfAbsent(proxy.getName(), l -> new ArrayList<>()).add(proxy);
        }
      }
    }
  }

  private SMTestProxy findChild(SMTestProxy parentSuite, String fullName, boolean preferSuite) {
    if (myTreeBuildBeforeStart) {
      Set<SMTestProxy> acceptedProxies = new LinkedHashSet<>();
      Collection<? extends SMTestProxy> children = myCurrentChildren.get(fullName);
      if (children == null) {
        initCurrentChildren(parentSuite, preferSuite);
        children = myCurrentChildren.get(fullName);
      }
      if (children != null) { //null if child started second time
        for (SMTestProxy proxy : children) {
          if (!proxy.isFinal() || preferSuite && proxy.isSuite()) {
            acceptedProxies.add(proxy);
          }
        }
        if (!acceptedProxies.isEmpty()) {
          return acceptedProxies.stream()
            .filter(proxy -> proxy.isSuite() == preferSuite && proxy.getParent() == parentSuite)
            .findFirst()
            .orElse(acceptedProxies.iterator().next());
        }
      }
    }
    return null;
  }

  @Override
  public void onTestFinished(@NotNull final TestFinishedEvent testFinishedEvent) {
    final String testName = testFinishedEvent.getName();
    final Long duration = testFinishedEvent.getDuration();
    final String fullTestName = getFullTestName(testName);
    final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);

    if (testProxy == null) {
      logProblem("Test wasn't started! TestFinished event: name = {" + testName + "}. " +
                 cannotFindFullTestNameMsg(fullTestName));
      return;
    }

    testProxy.setDuration(duration != null ? duration : 0);
    testProxy.setFrameworkOutputFile(testFinishedEvent.getOutputFile());
    testProxy.setFinished();
    myRunningTestsFullNameToProxy.remove(fullTestName);
    clearCurrentChildren(fullTestName, testProxy);

    //fire events
    fireOnTestFinished(testProxy);
  }

  private void clearCurrentChildren(String fullTestName, SMTestProxy testProxy) {
    myCurrentChildren.remove(fullTestName);
    String url = testProxy.getLocationUrl();
    if (url != null) {
      myCurrentChildren.remove(url);
    }
  }

  @Override
  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    //fire events
    final String suiteName = suiteFinishedEvent.getName();
    final SMTestProxy mySuite = mySuitesStack.popSuite(suiteName);
    if (mySuite != null) {
      mySuite.setFinished();
      myCurrentChildren.remove(suiteName);
      String locationUrl = mySuite.getLocationUrl();
      if (locationUrl != null) {
        myCurrentChildren.remove(locationUrl);
      }

      //fire events
      fireOnSuiteFinished(mySuite);
    }
  }

  @Override
  public void onUncapturedOutput(@NotNull final String text, final Key outputType) {
    final SMTestProxy currentProxy = findCurrentTestOrSuite();
    currentProxy.addOutput(text, outputType);
  }

  @Override
  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    final SMTestProxy currentProxy = findCurrentTestOrSuite();
    currentProxy.addError(localizedMessage, stackTrace, isCritical);
  }


  @Override
  public void onTestFailure(@NotNull final TestFailedEvent testFailedEvent) {
    // if hasn't been already reported
    // 1. report
    // 2. add failure
    // fire event
    final String testName = testFailedEvent.getName();
      if (testName == null) {
        logProblem("No test name specified in " + testFailedEvent);
        return;
      }
      final String localizedMessage = testFailedEvent.getLocalizedFailureMessage();
      final String stackTrace = testFailedEvent.getStacktrace();
      final boolean isTestError = testFailedEvent.isTestError();
      final String comparisionFailureActualText = testFailedEvent.getComparisonFailureActualText();
      final String comparisionFailureExpectedText = testFailedEvent.getComparisonFailureExpectedText();
      final boolean inDebugMode = SMTestRunnerConnectionUtil.isInDebugMode();

      final String fullTestName = getFullTestName(testName);
      SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
      if (testProxy == null) {
        logProblem("Test wasn't started! TestFailure event: name = {" + testName + "}" +
                   ", message = {" + localizedMessage + "}" +
                   ", stackTrace = {" + stackTrace + "}. " +
                   cannotFindFullTestNameMsg(fullTestName));
        if (inDebugMode) {
          return;
        }
        else {
          // if hasn't been already reported
          // 1. report
          onTestStarted(new TestStartedEvent(testName, null));
          // 2. add failure
          testProxy = getProxyByFullTestName(fullTestName);
        }
      }

      if (testProxy == null) {
        return;
      }

      if (comparisionFailureActualText != null && comparisionFailureExpectedText != null) {
        testProxy.setTestComparisonFailed(localizedMessage, stackTrace, comparisionFailureActualText, comparisionFailureExpectedText,
                                          testFailedEvent);
      }
      else if (comparisionFailureActualText == null && comparisionFailureExpectedText == null) {
        testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);
      }
      else {
        testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);
        logProblem("Comparison failure actual and expected texts should be both null or not null.\n"
                   + "Expected:\n"
                   + comparisionFailureExpectedText + "\n"
                   + "Actual:\n"
                   + comparisionFailureActualText);
      }

      // fire event
      fireOnTestFailed(testProxy);
  }

  @Override
  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
    // try to fix
    // 1. report test opened
    // 2. report failure
    // fire event
    final String testName = testIgnoredEvent.getName();
    if (testName == null) {
      logProblem("TestIgnored event: no name");
    }
    String ignoreComment = testIgnoredEvent.getIgnoreComment();
    final String stackTrace = testIgnoredEvent.getStacktrace();
    final String fullTestName = getFullTestName(testName);
    SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
    if (testProxy == null) {
      final boolean debugMode = SMTestRunnerConnectionUtil.isInDebugMode();
      logProblem("Test wasn't started! " +
                 "TestIgnored event: name = {" + testName + "}, " +
                 "message = {" + ignoreComment + "}. " +
                 cannotFindFullTestNameMsg(fullTestName));
      if (debugMode) {
        return;
      }
      else {
        // try to fix
        // 1. report test opened
        onTestStarted(new TestStartedEvent(testName, null));

        // 2. report failure
        testProxy = getProxyByFullTestName(fullTestName);
      }
    }
    if (testProxy == null) {
      return;
    }
    testProxy.setTestIgnored(ignoreComment, stackTrace);

    // fire event
    fireOnTestIgnored(testProxy);
  }

  @Override
  public void onTestOutput(@NotNull final TestOutputEvent testOutputEvent) {
    final String testName = testOutputEvent.getName();
    final String text = testOutputEvent.getText();
    final Key outputType = testOutputEvent.getOutputType();
    final String fullTestName = getFullTestName(testName);
    final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
    if (testProxy == null) {
      logProblem("Test wasn't started! TestOutput event: name = {" + testName + "}, " +
                 "outputType = " + outputType + ", " +
                 "text = {" + text + "}. " +
                 cannotFindFullTestNameMsg(fullTestName));
      return;
    }
    testProxy.addOutput(text, outputType);
  }

  @Override
  public void onTestsCountInSuite(final int count) {
    fireOnTestsCountInSuite(count);
  }

  @NotNull
  protected final SMTestProxy getCurrentSuite() {
    final SMTestProxy currentSuite = mySuitesStack.getCurrentSuite();

    if (currentSuite != null) {
      return currentSuite;
    }

    // current suite shouldn't be null otherwise test runner isn't correct
    // or may be we are in debug mode
    logProblem("Current suite is undefined. Root suite will be used.");
    return myTestsRootProxy;

  }

  protected String getFullTestName(final String testName) {
    // Test name should be unique
    return testName;
  }

  protected int getRunningTestsQuantity() {
    return myRunningTestsFullNameToProxy.size();
  }

  @Nullable
  protected SMTestProxy getProxyByFullTestName(final String fullTestName) {
    return myRunningTestsFullNameToProxy.get(fullTestName);
  }

  @TestOnly
  protected void clearInternalSuitesStack() {
    mySuitesStack.clear();
  }

  private String cannotFindFullTestNameMsg(String fullTestName) {
    return "Cant find running test for ["
              + fullTestName
              + "]. Current running tests: {"
              + dumpRunningTestsNames() + "}";
  }

  private StringBuilder dumpRunningTestsNames() {
    final Set<String> names = myRunningTestsFullNameToProxy.keySet();
    final StringBuilder namesDump = new StringBuilder();
    for (String name : names) {
      namesDump.append('[').append(name).append(']').append(',');
    }
    return namesDump;
  }


  /*
   * Remove listeners,  etc
   */
  @Override
  public void dispose() {
    super.dispose();
    if (!myRunningTestsFullNameToProxy.isEmpty()) {
      final Application application = ApplicationManager.getApplication();
      if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
        logProblem("Not all events were processed! " + dumpRunningTestsNames());
      }
    }
    myRunningTestsFullNameToProxy.clear();
    mySuitesStack.clear();
  }


  private SMTestProxy findCurrentTestOrSuite() {
    //if we can locate test - we will send output to it, otherwise to current test suite
    SMTestProxy currentProxy = null;
    Iterator<SMTestProxy> iterator = myRunningTestsFullNameToProxy.values().iterator();
    if (iterator.hasNext()) {
      //current test
      currentProxy = iterator.next();

      if (iterator.hasNext()) { //if there are multiple tests running call put output to the suite
        currentProxy = null;
      }
    }
    
    if (currentProxy == null) {
      //current suite
      //
      // ProcessHandler can fire output available event before processStarted event
      final SMTestProxy currentSuite = mySuitesStack.getCurrentSuite();
      currentProxy = currentSuite != null ? currentSuite : myTestsRootProxy;
    }
    return currentProxy;
  }
}
