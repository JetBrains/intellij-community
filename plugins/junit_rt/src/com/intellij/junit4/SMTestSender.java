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

/*
 * User: anna
 * Date: 03-Jun-2009
 */
package com.intellij.junit4;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.ComparisonFailure;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

class SMTestSender extends RunListener {
  private static final String MESSAGE_LENGTH_FOR_PATTERN_MATCHING = "idea.junit.message.length.threshold";
  private static final String JUNIT_FRAMEWORK_COMPARISON_NAME = ComparisonFailure.class.getName();
  private static final String ORG_JUNIT_COMPARISON_NAME = "org.junit.ComparisonFailure";
  
  private String myCurrentClassName;
  private String myParamName;
  private boolean myIgnoreTopSuite;

  public void testRunStarted(Description description) throws Exception {
    myCurrentClassName = myIgnoreTopSuite ? description.toString() : null;
  }

  public void testRunFinished(Result result) throws Exception {
    if (myParamName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + myParamName + "\']");
    }
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
    }
  }

  public void testStarted(Description description) throws Exception {
    final String className = JUnit4ReflectionUtil.getClassName(description);
    if (!className.equals(myCurrentClassName)) {
      if (myCurrentClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + myCurrentClassName + "\']");
      }
      myCurrentClassName = className;
      System.out.println("##teamcity[testSuiteStarted name =\'" + myCurrentClassName + "\']");
    }
    final String methodName = JUnit4ReflectionUtil.getMethodName(description);
    final int paramStart = methodName.indexOf('[');
    if (paramStart > -1) {
      final String paramName = methodName.substring(paramStart, methodName.length());
      if (!paramName.equals(myParamName)) {
        if (myParamName != null) {
          System.out.println("##teamcity[testSuiteFinished name=\'" + myParamName + "\']");
        }
        myParamName = paramName;
        System.out.println("##teamcity[testSuiteStarted name =\'" + myParamName + "\']");
      }
    }
    System.out.println("##teamcity[testStarted name=\'" + methodName + "\']");
  }

  public void testFinished(Description description) throws Exception {
    System.out.println("##teamcity[testFinished name=\'" + JUnit4ReflectionUtil.getMethodName(description) + "\']");
  }

  public void testFailure(Failure failure) throws Exception {
    final String failureMessage = failure.getMessage();
    final String trace = failure.getTrace();
    final Map attrs = new HashMap();
    attrs.put("name", JUnit4ReflectionUtil.getMethodName(failure.getDescription()));
    attrs.put("message", failureMessage != null ? failureMessage : "");
    final ComparisonFailureData notification = createExceptionNotification(failure.getException());
    if (notification != null) {
      attrs.put("expected", notification.getExpected());
      attrs.put("actual", notification.getActual());

      final int failureIdx = trace.indexOf(failureMessage);
      attrs.put("details", failureIdx > -1 ? trace.substring(failureIdx + failureMessage.length()) : trace);
      final String filePath = notification.getFilePath();
      if (filePath != null) {
        attrs.put("expectedFile", filePath);
      }
    } else {
      attrs.put("details", trace);
      attrs.put("error", "true");
    }

    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
  }

  public void testAssumptionFailure(Failure failure) {
    prepareIgnoreMessage(failure.getDescription(), false);
  }

  public synchronized void testIgnored(Description description) throws Exception {
    prepareIgnoreMessage(description, true);
  }

  private static void prepareIgnoreMessage(Description description, boolean commentMessage) {
    Map attrs = new HashMap();
    if (commentMessage) {
      try {
        final Ignore ignoredAnnotation = (Ignore)description.getAnnotation(Ignore.class);
        if (ignoredAnnotation != null) {
          final String val = ignoredAnnotation.value();
          if (val != null) {
            attrs.put("message", val);
          }
        }
      }
      catch (NoSuchMethodError ignored) {
        //junit < 4.4
      }
    }
    attrs.put("name", JUnit4ReflectionUtil.getMethodName(description));
    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_IGNORED, attrs));
  }

  private static boolean isComparisonFailure(Throwable throwable) {
    if (throwable == null) return false;
    return isComparisonFailure(throwable.getClass());
  }

  private static boolean isComparisonFailure(Class aClass) {
    if (aClass == null) return false;
    final String throwableClassName = aClass.getName();
    if (throwableClassName.equals(JUNIT_FRAMEWORK_COMPARISON_NAME) || throwableClassName.equals(ORG_JUNIT_COMPARISON_NAME)) return true;
    return isComparisonFailure(aClass.getSuperclass());
  }

  static ComparisonFailureData createExceptionNotification(Throwable assertion) {
    if (isComparisonFailure(assertion)) {
      return ComparisonFailureData.create(assertion);
    }
    try {
      final Throwable cause = assertion.getCause();
      if (isComparisonFailure(cause)) {
        return ComparisonFailureData.create(assertion);
      }
    }
    catch (Throwable ignore) {
    }
    final String message = assertion.getMessage();
    if (message != null  && acceptedByThreshold(message.length())) {
      try {
        return ExpectedPatterns.createExceptionNotification(message);
      }
      catch (Throwable ignored) {}
    }
    return null;
  }

  private static boolean acceptedByThreshold(int messageLength) {
    int threshold = 10000;
    try {
      final String property = System.getProperty(MESSAGE_LENGTH_FOR_PATTERN_MATCHING);
      if (property != null) {
        try {
          threshold = Integer.parseInt(property);
        }
        catch (NumberFormatException ignore) {}
      }
    }
    catch (SecurityException ignored) {}
    return messageLength < threshold;
  }

  private static void sendTree(JUnit4IdeaTestRunner runner, Object description, List tests) {
    if (tests.isEmpty()) {
      final String methodName = JUnit4ReflectionUtil.getMethodName((Description)description);
      System.out.println("##teamcity[suiteTreeNode name=\'" + methodName + 
                         "\' locationHint=\'java:test://" + JUnit4ReflectionUtil.getClassName((Description)description) + "." + methodName + "\']");
    }
    boolean pass = false;
    for (Iterator iterator = tests.iterator(); iterator.hasNext(); ) {
      final Object next = iterator.next();
      final List childTests = runner.getChildTests(next);
      final Description nextDescription = (Description)next;
      if ((childTests.isEmpty() || isParameter(nextDescription)) && !pass) {
        pass = true;
        final String className = JUnit4ReflectionUtil.getClassName((Description)description);
        String locationHint = className;
        if (isParameter((Description)description)) {
          final String displayName = nextDescription.getDisplayName();
          final int paramIdx = displayName.indexOf(locationHint);
          if (paramIdx > -1) {
            locationHint = displayName.substring(paramIdx + locationHint.length());
            if (locationHint.startsWith("(") && locationHint.endsWith(")")) {
              locationHint = locationHint.substring(1, locationHint.length() - 1) + "." + className; 
            }
          }
        }
        System.out.println("##teamcity[suiteTreeStarted name=\'" + className +
                           "\' locationHint=\'java:suite://" + locationHint + "\']");
      }
      sendTree(runner, next, childTests);
    }
    if (pass) {
      System.out.println("##teamcity[suiteTreeEnded name=\'" + JUnit4ReflectionUtil.getClassName((Description)description) + "\']");
    }
  }

  private static boolean isParameter(Description description) {
    String displayName = description.getDisplayName();
    return displayName.startsWith("[") && displayName.endsWith("]");
  }

  public void sendTree(JUnit4IdeaTestRunner runner, Description description) {
    final List tests = runner.getChildTests(description);
    if (tests.isEmpty()) {
      myIgnoreTopSuite = true;
    }
    sendTree(runner, description, tests);
  }
}