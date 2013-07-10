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
package com.intellij.junit3;

import com.intellij.rt.execution.junit.*;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.SegmentedOutputStream;
import junit.framework.*;
import junit.textui.ResultPrinter;
import junit.textui.TestRunner;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

public class JUnit3IdeaTestRunner extends TestRunner implements IdeaTestRunner {
  private TestListener myTestsListener;
  private JUnit3OutputObjectRegistry myRegistry;
  private ArrayList myListeners;
  private boolean mySendTree;

  public JUnit3IdeaTestRunner() {
    super(DeafStream.DEAF_PRINT_STREAM);
  }

  public int startRunnerWithArgs(String[] args, ArrayList listeners, boolean sendTree) {
    myListeners = listeners;
    mySendTree = sendTree;
    if (sendTree) {
      setPrinter(new TimeSender(myRegistry));
    }
    else {
      setPrinter(new MockResultPrinter());
    }
    try {
      Test suite = TestRunnerUtil.getTestSuite(this, args);
      if (suite == null) return -1;
      TestResult result = doRun(suite);
      if (!result.wasSuccessful()) {
        return -1;
      }
      return 0;
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }
  }

  public void clearStatus() {
    super.clearStatus();
  }

  public void runFailed(String message) {
    super.runFailed(message);
  }

  public void setStreams(SegmentedOutputStream segmentedOut, SegmentedOutputStream segmentedErr, int lastIdx) {
    myRegistry = new JUnit3OutputObjectRegistry(segmentedOut, lastIdx);
    myTestsListener = new TestResultsSender(myRegistry);
  }

  public Object getTestToStart(String[] args) {
    return TestRunnerUtil.getTestSuite(this, args);
  }

  public List getChildTests(Object description) {
    return getTestCasesOf((Test)description);
  }

  public OutputObjectRegistry getRegistry() {
    return myRegistry;
  }

  public String getTestClassName(Object child) {
    return child instanceof TestSuite ? ((TestSuite)child).getName() : child.getClass().getName();
  }

  public String getStartDescription(Object child) {
    final Test test = (Test)child;
    if (test instanceof TestCase) {
      return test.getClass().getName() + "," + ((TestCase)test).getName();
    }
    return test.toString();
  }

  protected TestResult createTestResult() {
    TestResult testResult = super.createTestResult();
    testResult.addListener(myTestsListener);
    try {
      for (int i = 0; i < myListeners.size(); i++) {
        final IDEAJUnitListener junitListener = (IDEAJUnitListener)Class.forName((String)myListeners.get(i)).newInstance();
        testResult.addListener(new TestListener() {
          public void addError(Test test, Throwable t) {}

          public void addFailure(Test test, AssertionFailedError t) {}

          public void endTest(Test test) {
            if (test instanceof TestCase) {
              junitListener.testFinished(test.getClass().getName(), ((TestCase)test).getName());
            }
          }

          public void startTest(Test test) {
            if (test instanceof TestCase) {
              junitListener.testStarted(test.getClass().getName(), ((TestCase)test).getName());
            }
          }
        });
      }
    }
    catch (Exception e) {
      //do nothing
    }
    return testResult;
  }

  public TestResult doRun(Test suite, boolean wait) {  //todo
    try {
      TreeSender.sendTree(this, suite, mySendTree);
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      System.err.println("Internal Error occured.");
      e.printStackTrace(System.err);
    }
    return super.doRun(suite, wait);
  }

  static Vector getTestCasesOf(Test test) {
    Vector testCases = new Vector();
    if (test instanceof TestRunnerUtil.SuiteMethodWrapper) {
      test = ((TestRunnerUtil.SuiteMethodWrapper)test).getSuite();
    }
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;

      for (Enumeration each = testSuite.tests(); each.hasMoreElements();) {
        Object childTest = each.nextElement();
        if (childTest instanceof TestSuite && !((TestSuite)childTest).tests().hasMoreElements()) continue;
        testCases.addElement(childTest);
      }
    }
    return testCases;
  }

  public static class MockResultPrinter extends ResultPrinter {
    public MockResultPrinter() {
      super(DeafStream.DEAF_PRINT_STREAM);
    }
  }
}
