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
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.execution.testframework.sm.SMTestsRunnerBundle;
import com.intellij.execution.testframework.sm.runner.events.*;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
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
  private static final Logger LOG = Logger.getInstance(GeneralToSMTRunnerEventsConvertor.class.getName());

  private final Map<String, SMTestProxy> myRunningTestsFullNameToProxy = new HashMap<String, SMTestProxy>();
  private final Set<AbstractTestProxy> myFailedTestsSet = new HashSet<AbstractTestProxy>();
  private final TestSuiteStack mySuitesStack = new TestSuiteStack();
  private final List<SMTRunnerEventsListener> myEventsListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final SMTestProxy.SMRootTestProxy myTestsRootNode;
  private final String myTestFrameworkName;

  private boolean myIsTestingFinished;
  private SMTestLocator myLocator = null;
  private boolean myTreeBuildBeforeStart = false;

  public GeneralToSMTRunnerEventsConvertor(@NotNull SMTestProxy.SMRootTestProxy testsRootNode, @NotNull String testFrameworkName) {
    myTestsRootNode = testsRootNode;
    myTestFrameworkName = testFrameworkName;
  }

  @Override
  public void setLocator(@NotNull SMTestLocator locator) {
    myLocator = locator;
  }

  public void addEventsListener(@NotNull final SMTRunnerEventsListener listener) {
    myEventsListeners.add(listener);
  }

  public void onStartTesting() {
    addToInvokeLater(new Runnable() {
      public void run() {
        mySuitesStack.pushSuite(myTestsRootNode);
        myTestsRootNode.setStarted();

        //fire
        fireOnTestingStarted();
      }
    });
  }

  @Override
  public void onTestsReporterAttached() {
    addToInvokeLater(new Runnable() {
      public void run() {
        myTestsRootNode.setTestsReporterAttached();
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
        if (!isTreeComplete(myRunningTestsFullNameToProxy.keySet(), myTestsRootNode)) {
          myTestsRootNode.setTerminated();
          myRunningTestsFullNameToProxy.clear();
        }
        mySuitesStack.clear();
        myTestsRootNode.setFinished();


        //fire events
        fireOnTestingFinished();
      }
    });
    stopEventProcessing();
  }

  @Override
  public void onRootPresentationAdded(final String rootName, final String comment, final String rootLocation) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        myTestsRootNode.setPresentation(rootName);
        myTestsRootNode.setComment(comment);
        myTestsRootNode.setRootLocationUrl(rootLocation);
        if (myLocator != null) {
          myTestsRootNode.setLocator(myLocator);
        }
      }
    });
  }

  @Override
  public void onSuiteTreeNodeAdded(final String testName, final String locationHint) {
    myTreeBuildBeforeStart = true;
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final SMTestProxy testProxy = new SMTestProxy(testName, false, locationHint);
        if (myLocator != null) {
          testProxy.setLocator(myLocator);
        }
        getCurrentSuite().addChild(testProxy);
        fireOnSuiteTreeNodeAdded(testProxy);
      }
    });
  }

  @Override
  public void onSuiteTreeStarted(final String suiteName, final String locationHint) {
    myTreeBuildBeforeStart = true;
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        final SMTestProxy parentSuite = getCurrentSuite();
        final SMTestProxy newSuite = new SMTestProxy(suiteName, true, locationHint);
        if (myLocator != null) {
          newSuite.setLocator(myLocator);
        }
        parentSuite.addChild(newSuite);

        mySuitesStack.pushSuite(newSuite);

        fireOnSuiteTreeStarted(newSuite);
      }
    });
  }

  @Override
  public void onSuiteTreeEnded(final String suiteName) {
    addToInvokeLater(new Runnable() {
      @Override
      public void run() {
        mySuitesStack.popSuite(suiteName);
      }
    });
  }

  @Override
  public void setPrinterProvider(@NotNull TestProxyPrinterProvider printerProvider) {
  }

  public void onTestStarted(@NotNull final TestStartedEvent testStartedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
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
        SMTestProxy testProxy = findChildByName(parentSuite, fullName);
        if (testProxy == null) {
          // creates test
          testProxy = new SMTestProxy(testName, false, locationUrl);
          testProxy.setConfig(isConfig);

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
    });
  }

  public void onSuiteStarted(@NotNull final TestSuiteStartedEvent suiteStartedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        final String suiteName = suiteStartedEvent.getName();
        final String locationUrl = suiteStartedEvent.getLocationUrl();

        SMTestProxy parentSuite = getCurrentSuite();
        SMTestProxy newSuite = findChildByName(parentSuite, suiteName);
        if (newSuite == null) {
          //new suite
          newSuite = new SMTestProxy(suiteName, true, locationUrl);

          if (myLocator != null) {
            newSuite.setLocator(myLocator);
          }

          parentSuite.addChild(newSuite);
        }

        mySuitesStack.pushSuite(newSuite);

        //Progress started
        newSuite.setStarted();

        //fire event
        fireOnSuiteStarted(newSuite);
      }
    });
  }

  private SMTestProxy findChildByName(SMTestProxy parentSuite, String fullName) {
    if (myTreeBuildBeforeStart) {
      for (SMTestProxy proxy : parentSuite.getChildren()) {
        if (fullName.equals(proxy.getName()) && !proxy.isFinal()) {
          return proxy;
        }
      }
    }
    return null;
  }

  public void onTestFinished(@NotNull final TestFinishedEvent testFinishedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
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
        testProxy.setFinished();
        myRunningTestsFullNameToProxy.remove(fullTestName);

        //fire events
        fireOnTestFinished(testProxy);
      }
    });
  }

  public void onSuiteFinished(@NotNull final TestSuiteFinishedEvent suiteFinishedEvent) {
    addToInvokeLater(new Runnable() {
      public void run() {
        final String suiteName = suiteFinishedEvent.getName();
        final SMTestProxy mySuite = mySuitesStack.popSuite(suiteName);
        if (mySuite != null) {
          mySuite.setFinished();

          //fire events
          fireOnSuiteFinished(mySuite);
        }
      }
    });
  }

  public void onUncapturedOutput(@NotNull final String text, final Key outputType) {
    addToInvokeLater(new Runnable() {
      public void run() {
        final SMTestProxy currentProxy = findCurrentTestOrSuite();

        if (ProcessOutputTypes.STDERR.equals(outputType)) {
          currentProxy.addStdErr(text);
        } else if (ProcessOutputTypes.SYSTEM.equals(outputType)) {
          currentProxy.addSystemOutput(text);
        } else {
          currentProxy.addStdOutput(text, outputType);
        }
      }
    });
  }

  public void onError(@NotNull final String localizedMessage,
                      @Nullable final String stackTrace,
                      final boolean isCritical) {
    addToInvokeLater(new Runnable() {
      public void run() {
        final SMTestProxy currentProxy = findCurrentTestOrSuite();
        currentProxy.addError(localizedMessage, stackTrace, isCritical);
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

  public void onCustomProgressTestFinished() {
    addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestFinished();
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
          } else {
            // try to fix the problem:
            if (!myFailedTestsSet.contains(testProxy)) {
              // if hasn't been already reported
              // 1. report
              onTestStarted(new TestStartedEvent(testName, null));
              // 2. add failure
              testProxy = getProxyByFullTestName(fullTestName);
            }
          }
        }

        if (testProxy == null) {
          return;
        }

        if (comparisionFailureActualText != null && comparisionFailureExpectedText != null) {
          if (myFailedTestsSet.contains(testProxy)) {
            // duplicate message
            logProblem("Duplicate failure for test [" + fullTestName + "]: msg = " + localizedMessage + ", stacktrace = " + stackTrace);

            if (inDebugMode) {
              return;
            }
          }

          testProxy.setTestComparisonFailed(localizedMessage, stackTrace,
                                            comparisionFailureActualText, comparisionFailureExpectedText, testFailedEvent.getFilePath());
        } else if (comparisionFailureActualText == null && comparisionFailureExpectedText == null) {
          testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);
        } else {
          logProblem("Comparison failure actual and expected texts should be both null or not null.\n"
                     + "Expected:\n"
                     + comparisionFailureExpectedText + "\n"
                     + "Actual:\n"
                     + comparisionFailureActualText);
        }

        myFailedTestsSet.add(testProxy);

        // fire event
        fireOnTestFailed(testProxy);
      }
    });
  }

  public void onTestIgnored(@NotNull final TestIgnoredEvent testIgnoredEvent) {
     addToInvokeLater(new Runnable() {
      public void run() {
        final String testName = ObjectUtils.assertNotNull(testIgnoredEvent.getName());
        String ignoreComment = testIgnoredEvent.getIgnoreComment();
        if (StringUtil.isEmpty(ignoreComment)) {
          ignoreComment = SMTestsRunnerBundle.message("sm.test.runner.states.test.is.ignored");
        }
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
      }
    });
  }

  public void onTestOutput(@NotNull final TestOutputEvent testOutputEvent) {
     addToInvokeLater(new Runnable() {
      public void run() {
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

  @NotNull
  protected final SMTestProxy getCurrentSuite() {
    final SMTestProxy currentSuite = mySuitesStack.getCurrentSuite();

    if (currentSuite != null) {
      return currentSuite;
    }

    // current suite shouldn't be null otherwise test runner isn't correct
    // or may be we are in debug mode
    logProblem("Current suite is undefined. Root suite will be used.");
    return myTestsRootNode;

  }
 
  protected String getFullTestName(final String testName) {
    // Test name should be unique
    return testName;
  }

  protected int getRunningTestsQuantity() {
    return myRunningTestsFullNameToProxy.size();
  }

  protected Set<AbstractTestProxy> getFailedTestsSet() {
    return Collections.unmodifiableSet(myFailedTestsSet);
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

  private void fireOnTestingStarted() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestingStarted(myTestsRootNode);
    }
  }

  private void fireOnTestingFinished() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestingFinished(myTestsRootNode);
    }
  }

  private void fireOnTestsCountInSuite(final int count) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onTestsCountInSuite(count);
    }
  }

  private void fireOnSuiteTreeNodeAdded(SMTestProxy testProxy) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onSuiteTreeNodeAdded(testProxy);
    }
  }

  private void fireOnSuiteTreeStarted(SMTestProxy suite) {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onSuiteTreeStarted(suite);
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

  private void fireOnCustomProgressTestFinished() {
    for (SMTRunnerEventsListener listener : myEventsListeners) {
      listener.onCustomProgressTestFinished();
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

        if (!myRunningTestsFullNameToProxy.isEmpty()) {
          final Application application = ApplicationManager.getApplication();
          if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            logProblem("Not all events were processed! " + dumpRunningTestsNames());
          }
        }
        myRunningTestsFullNameToProxy.clear();
        mySuitesStack.clear();
      }
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

  public static String getTFrameworkPrefix(final String testFrameworkName) {
    return "[" + testFrameworkName + "]: ";
  }

  private void logProblem(final String msg) {
    logProblem(LOG, msg, myTestFrameworkName);
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
