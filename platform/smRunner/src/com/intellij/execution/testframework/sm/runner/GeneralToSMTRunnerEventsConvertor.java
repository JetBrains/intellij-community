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

import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.sm.SMRunnerUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author: Roman Chernyatchik
 *
 * This class fires events to RTestUnitEventsListener in EventDispatch thread
 */
public class GeneralToSMTRunnerEventsConvertor implements GeneralTestEventsProcessor {
  private static final Logger LOG = Logger.getInstance(GeneralToSMTRunnerEventsConvertor.class.getName());

  private final Map<String, SMTestProxy> myRunningTestsFullNameToProxy = new HashMap<String, SMTestProxy>();

  private final Set<AbstractTestProxy> myFailedTestsSet = new HashSet<AbstractTestProxy>();

  private final TestSuiteStack mySuitesStack = new TestSuiteStack();
  private final List<SMTRunnerEventsListener> myEventsListeners = new ArrayList<SMTRunnerEventsListener>();
  private final SMTestProxy myTestsRootNode;
  private boolean myIsTestingFinished;

  public GeneralToSMTRunnerEventsConvertor(@NotNull final SMTestProxy testsRootNode) {
    myTestsRootNode = testsRootNode;
  }

  public void addEventsListener(final SMTRunnerEventsListener listener) {
    myEventsListeners.add(listener);
  }

  public void onStartTesting() {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        mySuitesStack.pushSuite(myTestsRootNode);
        myTestsRootNode.setStarted();

        //fire
        fireOnTestingStarted();
      }
    });
  }

  public void onFinishTesting() {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
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
        if (!myTestsRootNode.equals(mySuitesStack.getCurrentSuite())) {
          myTestsRootNode.setTerminated();
          myRunningTestsFullNameToProxy.clear();
        }
        mySuitesStack.clear();
        myTestsRootNode.setFinished();


        //fire events
        fireOnTestingFinished();
      }
    });
  }

  public void onTestStarted(final String testName, final String locationUrl) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullName = getFullTestName(testName);

        if (myRunningTestsFullNameToProxy.containsKey(fullName)) {
          //Dublicated event
          LOG.warn("Test [" + fullName + "] has been already started");
          return;
        }

        final SMTestProxy parentSuite = getCurrentSuite();

        // creates test
        final SMTestProxy testProxy = new SMTestProxy(testName, false, locationUrl);
        parentSuite.addChild(testProxy);
        // adds to running tests map
        myRunningTestsFullNameToProxy.put(fullName, testProxy);

        //Progress started
        testProxy.setStarted();

        //fire events
        fireOnTestStarted(testProxy);
      }
    });
  }

  public void onSuiteStarted(final String suiteName, @Nullable final String locationUrl) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final SMTestProxy parentSuite = getCurrentSuite();
        //new suite
        final SMTestProxy newSuite = new SMTestProxy(suiteName, true, locationUrl);
        parentSuite.addChild(newSuite);

        mySuitesStack.pushSuite(newSuite);

        //Progress started
        newSuite.setStarted();

        //fire event
        fireOnSuiteStarted(newSuite);
      }
    });
  }

  public void onTestFinished(final String testName, final int duration) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.warn("Test wasn't started! TestFinished event: name = {" + testName + "}. " +
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

  public void onSuiteFinished(final String suiteName) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final SMTestProxy mySuite = mySuitesStack.popSuite(suiteName);
        if (mySuite != null) {
          mySuite.setFinished();

          //fire events
          fireOnSuiteFinished(mySuite);
        }
      }
    });
  }

  public void onUncapturedOutput(final String text, final Key outputType) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
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
                      @Nullable final String stackTrace) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final SMTestProxy currentProxy = findCurrentTestOrSuite();
        currentProxy.addError(localizedMessage, stackTrace);
      }
    });
  }

  public void onCustomProgressTestsCategory(@Nullable final String categoryName,
                                            final int testCount) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestsCategory(categoryName, testCount);
      }
    });
  }

  public void onCustomProgressTestStarted() {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestStarted();
      }
    });
  }

  public void onCustomProgressTestFailed() {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        fireOnCustomProgressTestFailed();
      }
    });
  }

  public void onTestFailure(final String testName,
                            final String localizedMessage, final String stackTrace,
                            final boolean isTestError) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.warn("Test wasn't started! TestFailure event: name = {" + testName + "}" +
                   ", message = {" + localizedMessage + "}" +
                   ", stackTrace = {" + stackTrace + "}. " +
                   cannotFindFullTestNameMsg(fullTestName));
          return;
        }
        // if wasn't processed
        if (myFailedTestsSet.contains(testProxy)) {
          // dublicate message
          LOG.warn("Duplicate failure for test [" + fullTestName  + "]: msg = " + localizedMessage + ", stacktrace = " + stackTrace);
          return;
        }

        testProxy.setTestFailed(localizedMessage, stackTrace, isTestError);


        myFailedTestsSet.add(testProxy);

        // fire event
        fireOnTestFailed(testProxy);
      }
    });
  }

  public void onTestIgnored(@NotNull final String testName, 
                            @NotNull final String ignoreComment,
                            @Nullable final String stackTrace) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.warn("Test wasn't started! " +
                   "TestIgnored event: name = {" + testName + "}, " +
                   "message = {" + ignoreComment + "}. " +
                   cannotFindFullTestNameMsg(fullTestName));
          return;
        }

        testProxy.setTestIgnored(ignoreComment, stackTrace);

        // fire event
        fireOnTestIgnored(testProxy);
      }
    });
  }

  public void onTestOutput(final String testName,
                           final String text, final boolean stdOut) {
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        final String fullTestName = getFullTestName(testName);
        final SMTestProxy testProxy = getProxyByFullTestName(fullTestName);
        if (testProxy == null) {
          LOG.warn("Test wasn't started! TestOutput event: name = {" + testName + "}, " +
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
    SMRunnerUtil.addToInvokeLater(new Runnable() {
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
    LOG.warn("Current suite is undefined. Root suite will be used.");
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
    SMRunnerUtil.addToInvokeLater(new Runnable() {
      public void run() {
        myEventsListeners.clear();

        if (!myRunningTestsFullNameToProxy.isEmpty()) {
          final Application application = ApplicationManager.getApplication();
          if (!application.isHeadlessEnvironment() && !application.isUnitTestMode()) {
            LOG.warn("Not all events were processed! " + dumpRunningTestsNames());
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
}
