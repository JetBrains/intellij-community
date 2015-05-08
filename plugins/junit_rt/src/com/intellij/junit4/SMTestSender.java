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
import jetbrains.buildServer.messages.serviceMessages.MapSerializerUtil;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage;
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageTypes;
import junit.framework.ComparisonFailure;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.PrintStream;
import java.util.*;

public class SMTestSender extends RunListener {
  private static final String MESSAGE_LENGTH_FOR_PATTERN_MATCHING = "idea.junit.message.length.threshold";
  private static final String JUNIT_FRAMEWORK_COMPARISON_NAME = ComparisonFailure.class.getName();
  private static final String ORG_JUNIT_COMPARISON_NAME = "org.junit.ComparisonFailure";
  public static final String EMPTY_SUITE_NAME = "junit.framework.TestSuite$1";
  public static final String EMPTY_SUITE_WARNING = "warning";

  private String myCurrentSuiteName;
  private String myCurrentClassName;
  private String myParamName;

  private PrintStream myPrintStream = System.out;
  private final Map myParents = new HashMap();
  private final Map mySuites = new HashMap();


  public SMTestSender() {}

  public SMTestSender(PrintStream printStream) {
    myPrintStream = printStream;
  }

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  public void testRunStarted(Description description) throws Exception {
    myPrintStream.println("##teamcity[enteredTheMatrix]");
    if (myCurrentClassName != null && !myCurrentClassName.startsWith("[")) {
      int lastPointIdx = myCurrentClassName.lastIndexOf('.');
      String name = myCurrentClassName;
      String comment = null;
      if (lastPointIdx >= 0) {
        name = myCurrentClassName.substring(lastPointIdx + 1);
        comment = myCurrentClassName.substring(0, lastPointIdx);
      }

      myPrintStream.println("##teamcity[rootName name = \'" + escapeName(name) + 
                            (comment != null ? ("\' comment = \'" + escapeName(comment)) : "") + "\'" +
                            " location = \'java:suite://" + escapeName(myCurrentClassName) +
                            "\']");
      myCurrentClassName = getShortName(myCurrentClassName);
    }
  }

  public void testRunFinished(Result result) throws Exception {
    if (myParamName != null) {
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(myParamName) + "\']");
    }
    if (myCurrentClassName != null) {
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']");
    }
    if (myCurrentSuiteName != null) {
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(getShortName(myCurrentSuiteName)) + "\']");
    }
  }

  public void testStarted(Description description) throws Exception {
    final String methodName = JUnit4ReflectionUtil.getMethodName(description);
    final String classFQN = JUnit4ReflectionUtil.getClassName(description);
    final int paramStart = methodName.indexOf('[');
    if (myParamName != null){
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(myParamName) + "\']");
      myParamName = null;
    }

    final List suites = (List)myParents.get(description);
    if (suites != null && !suites.isEmpty()) {
      String currentSuite = (String)suites.get(0);
      List descriptors = (List)mySuites.get(currentSuite);

      if (descriptors.isEmpty()) {
        currentSuite = (String)suites.get(1);
        descriptors = (List)mySuites.get(currentSuite);
      }

      if (!currentSuite.equals(myCurrentSuiteName)) {
        finishCurrentSuite();
        myCurrentSuiteName = currentSuite;
        myPrintStream.println("##teamcity[testSuiteStarted name =\'" + escapeName(getShortName(myCurrentSuiteName)) + "\']");
      }

      descriptors.remove(description);
    }
    else if (myCurrentSuiteName != null){
      finishCurrentSuite();
      myCurrentSuiteName = null;
    }

    String className = getShortName(classFQN);
    if (!myEmptyTests.isEmpty() && isWarning(methodName, classFQN)) {
      className = (String)myEmptyTests.remove(0);
    }

    if (!className.equals(myCurrentClassName)) {
      if (myCurrentClassName != null) {
        myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']");
      }
      myCurrentClassName = className;
      myPrintStream.println("##teamcity[testSuiteStarted name =\'" + escapeName(myCurrentClassName) + "\']");
    }
    if (paramStart > -1) {
      final String paramName = methodName.substring(paramStart, methodName.length());
      if (!paramName.equals(myParamName)) {
        myParamName = paramName;
        myPrintStream.println("##teamcity[testSuiteStarted name =\'" + escapeName(myParamName) + "\']");
      }
    }
    myPrintStream.println("##teamcity[testStarted name=\'" + escapeName(methodName) + "\' " + 
                          getTestMethodLocation(methodName, classFQN) + "]");
  }

  private void finishCurrentSuite() {
    if (myCurrentClassName != null) {
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']");
      myCurrentClassName = null;
    }
    if (myCurrentSuiteName != null) {
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(getShortName(myCurrentSuiteName)) + "\']");
    }
  }

  public void testFinished(Description description) throws Exception {
    myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(JUnit4ReflectionUtil.getMethodName(description)) + "\']");
  }

  public void testFailure(Failure failure) throws Exception {
    final String failureMessage = failure.getMessage();
    final String trace = failure.getTrace();
    final Map attrs = new HashMap();
    attrs.put("name", JUnit4ReflectionUtil.getMethodName(failure.getDescription()));
    final ComparisonFailureData notification = createExceptionNotification(failure.getException());
    ComparisonFailureData.registerSMAttributes(notification, trace, failureMessage, attrs);
    myPrintStream.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
  }

  public void testAssumptionFailure(Failure failure) {
    prepareIgnoreMessage(failure.getDescription(), false);
  }

  public synchronized void testIgnored(Description description) throws Exception {
    testStarted(description);
    prepareIgnoreMessage(description, true);
    testFinished(description);
  }

  private void prepareIgnoreMessage(Description description, boolean commentMessage) {
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
    myPrintStream.println(ServiceMessage.asString(ServiceMessageTypes.TEST_IGNORED, attrs));
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

  private final List myEmptyTests = new ArrayList();  
  
  private void sendTree(Description description, Description parent, Description suiteParent) {
    String className = JUnit4ReflectionUtil.getClassName(description);
    if (description.getChildren().isEmpty()) {
      final String methodName = JUnit4ReflectionUtil.getMethodName((Description)description);
      if (methodName != null) {
        if (isWarning(methodName, className)) {
          className = JUnit4ReflectionUtil.getClassName(parent);
          myEmptyTests.add(getShortName(className));
        }
        myPrintStream.println("##teamcity[suiteTreeNode name=\'" + escapeName(methodName) + "\' " + getTestMethodLocation(methodName, className) + "]");
      }

      if (suiteParent != null ) {
        final String parentFQName = JUnit4ReflectionUtil.getClassName(suiteParent);
        if (!myCurrentClassName.equals(parentFQName)) {
          List parents = (List)myParents.get(description);
          if (parents == null) {
            parents = new ArrayList();
            myParents.put(description, parents);
          }
          if (!parents.contains(parentFQName)) {
            parents.add(parentFQName);
          }

          List descriptors = (List)mySuites.get(parentFQName);
          if (descriptors == null) {
            descriptors = new ArrayList();
            mySuites.put(parentFQName, descriptors);
          }
          descriptors.add(description);
        }
      }

      return;
    }
   
    List tests = description.getChildren();
    boolean pass = false;
    for (Iterator iterator = tests.iterator(); iterator.hasNext(); ) {
      final Object next = iterator.next();
      final Description nextDescription = (Description)next;
      final List childTests = nextDescription.getChildren();
      if ((myCurrentClassName == null || !myCurrentClassName.equals(className)) && !pass) {
        pass = true;
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
        myPrintStream.println("##teamcity[suiteTreeStarted name=\'" + escapeName(getShortName(className)) + "\' locationHint=\'java:suite://" + escapeName(locationHint) + "\']");
      }
      sendTree(nextDescription, description, isParameter(description) ? null : childTests.isEmpty() && parent != null ? parent : description);
    }
    if (pass) {
      myPrintStream.println("##teamcity[suiteTreeEnded name=\'" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName((Description)description))) + "\']");
    }
  }

  private static boolean isWarning(String methodName, String className) {
    return EMPTY_SUITE_WARNING.equals(methodName) && EMPTY_SUITE_NAME.equals(className);
  }

  private static String getTestMethodLocation(String methodName, String className) {
    return "locationHint=\'java:test://" + escapeName(className + "." + methodName) + "\'";
  }

  private static boolean isParameter(Description description) {
    String displayName = description.getDisplayName();
    return displayName.startsWith("[") && displayName.endsWith("]");
  }

  public void sendTree(Description description) {
    myCurrentClassName = JUnit4ReflectionUtil.getClassName((Description)description);
    sendTree(description, null, null);
  }

  private static String getShortName(String fqName) {
    if (fqName == null) return null;
    if (fqName.startsWith("[")) {
      //param name
      return fqName;
    }
    int lastPointIdx = fqName.lastIndexOf('.');
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }
}