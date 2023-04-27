// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit3;

import com.intellij.rt.execution.junit.ComparisonDetailsExtractor;
import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import com.intellij.rt.junit.DeafStream;
import com.intellij.rt.junit.IdeaTestRunner;
import junit.framework.*;
import junit.textui.ResultPrinter;
import junit.textui.TestRunner;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class JUnit3IdeaTestRunner extends TestRunner implements IdeaTestRunner<Test> {
  private SMTestListener myTestsListener;
  private ArrayList<String> myListeners;

  public JUnit3IdeaTestRunner() {
    super(DeafStream.DEAF_PRINT_STREAM);
  }

  @Override
  public void createListeners(ArrayList<String> listeners, int count) {
    myTestsListener = new SMTestListener();
    myListeners = listeners;
  }

  @Override
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

  @Override
  public void clearStatus() {
    super.clearStatus();
  }

  @Override
  public void runFailed(String message) {
    super.runFailed(message);
  }

  @Override
  public Test getTestToStart(String[] args, String name) {
    return TestRunnerUtil.getTestSuite(this, args);
  }

  @Override
  public List<Test> getChildTests(Test description) {
    return getTestCasesOf(description);
  }

  @Override
  public String getTestClassName(Test child) {
    return child instanceof TestSuite ? ((TestSuite)child).getName() : child.getClass().getName();
  }

  @Override
  public String getStartDescription(Test child) {
    if (child instanceof TestCase) {
      return child.getClass().getName() + "," + ((TestCase)child).getName();
    }
    return child.toString();
  }

  @Override
  protected TestResult createTestResult() {
    TestResult testResult = super.createTestResult();
    testResult.addListener(myTestsListener);
    try {
      for (String listener : myListeners) {
        final IDEAJUnitListener junitListener = Class.forName(listener).asSubclass(IDEAJUnitListener.class).getConstructor().newInstance();
        testResult.addListener(new TestListener() {
          @Override
          public void addError(Test test, Throwable t) {}

          @Override
          public void addFailure(Test test, AssertionFailedError t) {}

          @Override
          public void endTest(Test test) {
            if (test instanceof TestCase) {
              junitListener.testFinished(test.getClass().getName(), ((TestCase)test).getName());
            }
          }

          @Override
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

  @Override
  public TestResult doRun(Test suite, boolean wait) {  //todo
    final TestResult testResult = super.doRun(suite, wait);
    myTestsListener.finishSuite();

    return testResult;
  }

  static List<Test> getTestCasesOf(Test test) {
    List<Test> testCases = new ArrayList<>();
    if (test instanceof TestRunnerUtil.SuiteMethodWrapper) {
      test = ((TestRunnerUtil.SuiteMethodWrapper)test).getSuite();
    }
    if (test instanceof TestSuite) {
      TestSuite testSuite = (TestSuite)test;

      for (Enumeration<Test> each = testSuite.tests(); each.hasMoreElements();) {
        Test childTest = each.nextElement();
        if (childTest instanceof TestSuite && !((TestSuite)childTest).tests().hasMoreElements()) continue;
        testCases.add(childTest);
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

    @Override
    public void addError(Test test, Throwable e) {
      testFailure(e, MapSerializerUtil.TEST_FAILED, getMethodName(test));
    }

    private void testFailure(Throwable failure, String messageName, String methodName) {
      final Map<String, String> attrs = new HashMap<>();
      attrs.put("name", methodName);
      final long duration = System.currentTimeMillis() - myCurrentTestStart;
      if (duration > 0) {
        attrs.put("duration", Long.toString(duration));
      }
      try {
        final String trace = getTrace(failure);
        ComparisonFailureData notification = null;
        if (failure.getClass().getName().equals("com.intellij.rt.execution.junit.FileComparisonFailure")) {
          notification = ComparisonFailureData.create(failure);
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

    @Override
    public void addFailure(Test test, AssertionFailedError e) {
      addError(test, e);
    }

    @Override
    public void endTest(Test test) {
      final long duration = System.currentTimeMillis() - myCurrentTestStart;
      System.out.println("\n##teamcity[testFinished name='" + escapeName(getMethodName(test)) +
                         (duration > 0 ? "' duration='" + duration : "") + "']");
    }

    @Override
    public void startTest(Test test) {
      myCurrentTestStart = System.currentTimeMillis();
      final String className = getClassName(test);
      if (className != null && !className.equals(myClassName)) {
        finishSuite();
        myClassName = className;
        System.out.println("##teamcity[testSuiteStarted name ='" + escapeName(myClassName) +
                           "' locationHint='java:suite://" + escapeName(className) + "']");
      }
      final String methodName = getMethodName(test);
      System.out.println("##teamcity[testStarted name='" + escapeName(methodName) +
                         "' locationHint='java:test://" + escapeName(className + "/" + methodName) + "']");
    }

    protected void finishSuite() {
      if (myClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name='" + escapeName(myClassName) + "']");
      }
    }

    private static String escapeName(String str) {
      return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
    }
  }
}
