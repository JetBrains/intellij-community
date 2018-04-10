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
import junit.framework.*;
import junit.textui.ResultPrinter;
import junit.textui.TestRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class JUnit3IdeaTestRunner extends TestRunner implements IdeaTestRunner {
  private SMTestListener myTestsListener;
  private ArrayList myListeners;

  public JUnit3IdeaTestRunner() {
    super(DeafStream.DEAF_PRINT_STREAM);
  }

  public void createListeners(ArrayList listeners, int count) {
    myTestsListener = new SMTestListener();
    myListeners = listeners;
  }

  public int startRunnerWithArgs(String[] args, String name, int count, boolean sendTree) {
    setPrinter(new MockResultPrinter());
    try {
      Test suite = TestRunnerUtil.getTestSuite(this, args);
      if (suite == null) return -1;
      return doRun(suite).wasSuccessful() ? 0 : -1;
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

  public Object getTestToStart(String[] args, String name) {
    return TestRunnerUtil.getTestSuite(this, args);
  }

  public List getChildTests(Object description) {
    return getTestCasesOf((Test)description);
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
    final TestResult testResult = super.doRun(suite, wait);
    myTestsListener.finishSuite();

    return testResult;
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

  private static class SMTestListener implements TestListener {
    private String myClassName;
    private long myCurrentTestStart;

    public void addError(Test test, Throwable e) {
      testFailure(e, MapSerializerUtil.TEST_FAILED, getMethodName(test));
    }

    private void testFailure(Throwable failure, String messageName, String methodName) {
      final Map attrs = new HashMap();
      attrs.put("name", methodName);
      final long duration = System.currentTimeMillis() - myCurrentTestStart;
      if (duration > 0) {
        attrs.put("duration", Long.toString(duration));
      }
      try {
        final String trace = getTrace(failure);
        ComparisonFailureData notification = null;
        if (failure instanceof FileComparisonFailure) {
          final FileComparisonFailure comparisonFailure = (FileComparisonFailure)failure;
          notification = new ComparisonFailureData(comparisonFailure.getExpected(), comparisonFailure.getActual(), 
                                                   comparisonFailure.getFilePath(), comparisonFailure.getActualFilePath());
        }
        else if (failure instanceof ComparisonFailure || failure.getClass().getName().equals("org.junit.ComparisonFailure")) {
          notification = new ComparisonFailureData(ComparisonDetailsExtractor.getExpected(failure), ComparisonDetailsExtractor.getActual(failure));
        }
        ComparisonFailureData.registerSMAttributes(notification, trace, failure.getMessage(), attrs, failure);
      }
      catch (Throwable e) {
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter writer = new PrintWriter(stringWriter);
        e.printStackTrace(writer);
        ComparisonFailureData.registerSMAttributes(null, stringWriter.toString(), e.getMessage(), attrs, e);
      }
      finally {
        System.out.println("\n" + MapSerializerUtil.asString(messageName, attrs));
      }
    }

    public String getTrace(Throwable failure) {
      StringWriter stringWriter = new StringWriter();
      PrintWriter writer = new PrintWriter(stringWriter);
      failure.printStackTrace(writer);
      return stringWriter.toString();
    }
    
    private static String getMethodName(Test test) {
      final String toString = test.toString();
      final int braceIdx = toString.indexOf("(");
      return braceIdx > 0 ? toString.substring(0, braceIdx) : toString;
    }
    
    private static String getClassName(Test test) {
      final String toString = test.toString();
      final int braceIdx = toString.indexOf("(");
      return braceIdx > 0 && toString.endsWith(")") ? toString.substring(braceIdx + 1, toString.length() - 1) : null;
    }

    public void addFailure(Test test, AssertionFailedError e) {
      addError(test, e);
    }

    public void endTest(Test test) {
      final long duration = System.currentTimeMillis() - myCurrentTestStart;
      System.out.println("\n##teamcity[testFinished name=\'" + escapeName(getMethodName(test)) + 
                         (duration > 0 ? "\' duration=\'"  + Long.toString(duration) : "") + "\']");
    }

    public void startTest(Test test) {
      myCurrentTestStart = System.currentTimeMillis();
      final String className = getClassName(test);
      if (className != null && !className.equals(myClassName)) {
        finishSuite();
        myClassName = className;
        System.out.println("##teamcity[testSuiteStarted name =\'" + escapeName(myClassName) + 
                           "\' locationHint=\'java:suite://" + escapeName(className) + "\']");
      }
      final String methodName = getMethodName(test);
      System.out.println("##teamcity[testStarted name=\'" + escapeName(methodName) + 
                         "\' locationHint=\'java:test://" + escapeName(className + "." + methodName) + "\']");
    }

    protected void finishSuite() {
      if (myClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myClassName) + "\']");
      }
    }

    private static String escapeName(String str) {
      return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
    }
  }
}
