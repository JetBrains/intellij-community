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
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.util.Function;
import com.intellij.util.ObjectUtils;
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

  private final Map<String, SMTestProxy> myRunningTestsFullNameToProxy = new HashMap<>();
  private final TestSuiteStack mySuitesStack;
  private final Set<SMTestProxy> myCurrentChildren = new LinkedHashSet<>();
  private boolean myGetChildren = true;
  private final SMTestProxy.SMRootTestProxy myTestsRootNode;

  private boolean myIsTestingFinished;
  private SMTestLocator myLocator = null;
  private boolean myTreeBuildBeforeStart = false;

  public GeneralToSMTRunnerEventsConvertor(Project project, @NotNull SMTestProxy.SMRootTestProxy testsRootNode,
                                           @NotNull String testFrameworkName) {
    super(project, testFrameworkName);
    myTestsRootNode = testsRootNode;
    mySuitesStack = new TestSuiteStack(testFrameworkName);
  }

  @Override
  public void setLocator(@NotNull SMTestLocator locator) {
    myLocator = locator;
  }

  public void onStartTesting() {
    addToInvokeLater(() -> {
      mySuitesStack.pushSuite(myTestsRootNode);
      myTestsRootNode.setStarted();

      //fire
      fireOnTestingStarted(myTestsRootNode);
    });
  }

  @Override
  public void onTestsReporterAttached() {
    addToInvokeLater(() -> fireOnTestsReporterAttached(myTestsRootNode));
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
      // Lets assume, if at finish all suites except root suite are passed
      // then all is ok otherwise process was terminated by user
      if (!isTreeComplete(myRunningTestsFullNameToProxy.keySet(), myTestsRootNode)) {
        myTestsRootNode.setTerminated();
        myRunningTestsFullNameToProxy.clear();
      }
      mySuitesStack.clear();
      myTestsRootNode.setFinished();


      //fire events
      fireOnTestingFinished(myTestsRootNode);
    });
    stopEventProcessing();
  }

  @Override
  public void onRootPresentationAdded(final String rootName, final String comment, final String rootLocation) {
    addToInvokeLater(() -> {
      myTestsRootNode.setPresentation(rootName);
      myTestsRootNode.setComment(comment);
      myTestsRootNode.setRootLocationUrl(rootLocation);
      if (myLocator != null) {
        myTestsRootNode.setLocator(myLocator);
      }
    });
  }

  private final List<Runnable> myBuildTreeRunnables = new ArrayList<>();
  
  @Override
  public void onSuiteTreeNodeAdded(final String testName, final String locationHint) {
    myTreeBuildBeforeStart = true;
    myBuildTreeRunnables.add(() -> {
      final SMTestProxy testProxy = new SMTestProxy(testName, false, locationHint);
      if (myLocator != null) {
        testProxy.setLocator(myLocator);
      }
      getCurrentSuite().addChild(testProxy);
      myEventPublisher.onSuiteTreeNodeAdded(testProxy);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onSuiteTreeNodeAdded(testProxy);
      }
    });
  }

  @Override
  public void onSuiteTreeStarted(final String suiteName, final String locationHint) {
    myTreeBuildBeforeStart = true;
    myBuildTreeRunnables.add(() -> {
      final SMTestProxy parentSuite = getCurrentSuite();
      final SMTestProxy newSuite = new SMTestProxy(suiteName, true, locationHint);
      if (myLocator != null) {
        newSuite.setLocator(myLocator);
      }
      parentSuite.addChild(newSuite);

      mySuitesStack.pushSuite(newSuite);

      myEventPublisher.onSuiteTreeStarted(newSuite);
      for (SMTRunnerEventsListener adapter : myListenerAdapters) {
        adapter.onSuiteTreeStarted(newSuite);
      }
    });
  }

  @Override
  public void onSuiteTreeEnded(final String suiteName) {
    myBuildTreeRunnables.add(() -> mySuitesStack.popSuite(suiteName));
    
    if (myBuildTreeRunnables.size() > 100) {
      final ArrayList<Runnable> runnables = new ArrayList<>(myBuildTreeRunnables);
      myBuildTreeRunnables.clear();
      processTreeBuildEvents(runnables);
    }
  }

  @Override
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

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
  }

  public void onTestStarted(@NotNull final TestStartedEvent testStartedEvent) {
    addToInvokeLater(() -> {
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
      SMTestProxy testProxy = locationUrl != null ? findChildByLocation(parentSuite, locationUrl)
                                                  : findChildByName(parentSuite, fullName);
      if (testProxy == null) {
        // creates test
        testProxy = new SMTestProxy(testName, false, locationUrl);
        testProxy.setConfig(isConfig);

        if (myLocator != null) {
          testProxy.setLocator(myLocator);
        }

        parentSuite.addChild(testProxy);

        if (myTreeBuildBeforeStart && myGetChildren) {
          for (SMTestProxy proxy : parentSuite.getChildren()) {
            if (!proxy.isFinal()) {
              myCurrentChildren.add(proxy);
            }
          }
          myGetChildren = false;
        }
      }

      // adds to running tests map
      myRunningTestsFullNameToProxy.put(fullName, testProxy);

      //Progress started
      testProxy.setStarted();

      //fire events
      fireOnTestStarted(testProxy);
    });
  }

  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    addToInvokeLater(() -> {
      final String suiteName = suiteStartedEvent.getName();
      final String locationUrl = suiteStartedEvent.getLocationUrl();

      SMTestProxy parentSuite = getCurrentSuite();
      SMTestProxy newSuite = locationUrl != null ? findChildByLocation(parentSuite, locationUrl)
                                                 : findChildByName(parentSuite, suiteName);
      if (newSuite == null) {
        //new suite
        newSuite = new SMTestProxy(suiteName, true, locationUrl, parentSuite.isPreservePresentableName());

        if (myLocator != null) {
          newSuite.setLocator(myLocator);
        }

        parentSuite.addChild(newSuite);
      }

      myGetChildren = true;
      mySuitesStack.pushSuite(newSuite);

      //Progress started
      newSuite.setSuiteStarted();

      //fire event
      fireOnSuiteStarted(newSuite);
    });
  }

  private SMTestProxy findChildByName(SMTestProxy parentSuite, String fullName) {
    return findChild(parentSuite, fullName, SMTestProxy::getName);
  }

  private SMTestProxy findChildByLocation(SMTestProxy parentSuite, String fullName) {
    return findChild(parentSuite, fullName, SMTestProxy::getLocationUrl);
  }

  private SMTestProxy findChild(SMTestProxy parentSuite, String fullName, final Function<SMTestProxy, String> nameFunction) {
    if (myTreeBuildBeforeStart) {
      final Collection<? extends SMTestProxy> children = myGetChildren ? parentSuite.getChildren() : myCurrentChildren;
      for (SMTestProxy proxy : children) {
        if (fullName.equals(nameFunction.fun(proxy)) && !proxy.isFinal()) {
          return proxy;
        }
      }
    }
    return null;
  }

  public void onTestFinished(@NotNull final TestFinishedEvent testFinishedEvent) {
    addToInvokeLater(() -> {
      final String testName = testFinishedEvent.getName();
      final long duration = testFinishedEvent.getDuration();
      final String fullTestName = getFullTestName(testName);
      final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);

      if (testProxy == null) {
        logProblem("Test wasn't started! TestFinished event: name = {" + testName + "}. " +
                   cannotFindFullTestNameMsg(fullTestName));
        return;
      }

      testProxy.setDuration(duration);
      testProxy.setFrameworkOutputFile(testFinishedEvent.getOutputFile());
      testProxy.setFinished();
      myRunningTestsFullNameToProxy.remove(fullTestName);
      myCurrentChildren.remove(testProxy);

      //fire events
      fireOnTestFinished(testProxy);
    });
  }

  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    addToInvokeLater(() -> {
      final String suiteName = suiteFinishedEvent.getName();
      final SMTestProxy mySuite = mySuitesStack.popSuite(suiteName);
      if (mySuite != null) {
        mySuite.setFinished();
        myCurrentChildren.clear();
        myGetChildren = true;

        //fire events
        fireOnSuiteFinished(mySuite);
      }
    });
  }

  public void onUncapturedOutput(@NotNull final String text, final Key outputType) {
    addToInvokeLater(() -> {
      final SMTestProxy currentProxy = findCurrentTestOrSuite();

      if (ProcessOutputTypes.STDERR.equals(outputType)) {
        currentProxy.addStdErr(text);
      } else if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
        currentProxy.addSystemOutput(text);
      } else {
        currentProxy.addStdOutput(text, outputType);
      }
    });
  }

  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    addToInvokeLater(() -> {
      final SMTestProxy currentProxy = findCurrentTestOrSuite();
      currentProxy.addError(localizedMessage, stackTrace, isCritical);
    });
  }
  

  public void onTestFailure(@NotNull final TestFailedEvent testFailedEvent) {
    addToInvokeLater(() -> {
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
        testProxy.setTestComparisonFailed(localizedMessage, stackTrace,
                                          comparisionFailureActualText, comparisionFailureExpectedText,
                                          testFailedEvent.getExpectedFilePath(), testFailedEvent.getActualFilePath());
      }
      else if (comparisionFailureActualText == null && comparisionFailureExpectedText == null) {
        testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);
      }
      else {
        logProblem("Comparison failure actual and expected texts should be both null or not null.\n"
                   + "Expected:\n"
                   + comparisionFailureExpectedText + "\n"
                   + "Actual:\n"
                   + comparisionFailureActualText);
      }

      // fire event
      fireOnTestFailed(testProxy);
    });
  }

  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
     addToInvokeLater(() -> {
       final String testName = ObjectUtils.assertNotNull(testIgnoredEvent.getName());
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
         } else {
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
     });
  }

  public void onTestOutput(@NotNull final TestOutputEvent testOutputEvent) {
     addToInvokeLater(() -> {
       final String testName = testOutputEvent.getName();
       final String text = testOutputEvent.getText();
       final boolean stdOut = testOutputEvent.isStdOut();
       final String fullTestName = getFullTestName(testName);
       final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
       if (testProxy == null) {
         logProblem("Test wasn't started! TestOutput event: name = {" + testName + "}, " +
                    "isStdOut = " + stdOut + ", " +
                    "text = {" + text + "}. " +
                    cannotFindFullTestNameMsg(fullTestName));
         return;
       }

       if (stdOut) {
         testProxy.addStdOutput(text, ProcessOutputTypes.STDOUT);
       } else {
         testProxy.addStdErr(text);
       }
     });
  }

  public void onTestsCountInSuite(final int count) {
     addToInvokeLater(() -> fireOnTestsCountInSuite(count));
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
    myGetChildren = true;
    return myTestsRootNode;

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
  public void dispose() {
    super.dispose();
     addToInvokeLater(() -> {

       disconnectListeners();
       if (!myRunningTestsFullNameToProxy.isEmpty()) {
         final Application application = ApplicationManager.getApplication();
         if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
           logProblem("Not all events were processed! " + dumpRunningTestsNames());
         }
       }
       myRunningTestsFullNameToProxy.clear();
       mySuitesStack.clear();
     });
  }


  private SMTestProxy findCurrentTestOrSuite() {
    //if we can locate test - we will send output to it, otherwise to current test suite
    final SMTestProxy currentProxy;
    if (myRunningTestsFullNameToProxy.size() == 1) {
      //current test
      currentProxy = myRunningTestsFullNameToProxy.values().iterator().next();
    } else {
      //current suite
      //
      // ProcessHandler can fire output available event before processStarted event
      currentProxy = mySuitesStack.isEmpty() ? myTestsRootNode : getCurrentSuite();
    }
    return currentProxy;
  }
}
