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
  
  private String myCurrentClassName;
  private String myParamName;
  private boolean myIgnoreTopSuite;

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  public void testRunStarted(Description description) throws Exception {
    myCurrentClassName = myIgnoreTopSuite ? getShortName(description.toString()) : null;
    System.out.println("##teamcity[enteredTheMatrix]\n");
  }

  public void testRunFinished(Result result) throws Exception {
    if (myParamName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myParamName) + "\']\n");
    }
    if (myCurrentClassName != null) {
      System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']\n");
    }
  }

  public void testStarted(Description description) throws Exception {
    final String methodName = JUnit4ReflectionUtil.getMethodName(description);
    final int paramStart = methodName.indexOf('[');
    if (myParamName != null){
      System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myParamName) + "\']");
      myParamName = null;
    }
    final String classFQN = JUnit4ReflectionUtil.getClassName(description);
    final String className = getShortName(classFQN);
    if (!className.equals(myCurrentClassName)) {
      if (myCurrentClassName != null) {
        System.out.println("##teamcity[testSuiteFinished name=\'" + escapeName(myCurrentClassName) + "\']");
      }
      myCurrentClassName = className;
      System.out.println("##teamcity[testSuiteStarted name =\'" + escapeName(myCurrentClassName) + "\']");
    }
    if (paramStart > -1) {
      final String paramName = methodName.substring(paramStart, methodName.length());
      if (!paramName.equals(myParamName)) {
        myParamName = paramName;
        System.out.println("##teamcity[testSuiteStarted name =\'" + escapeName(myParamName) + "\']");
      }
    }
    System.out.println("##teamcity[testStarted name=\'" + escapeName(methodName) + "\'" + getTestMethodLocation(methodName, classFQN)+ "]");
  }

  public void testFinished(Description description) throws Exception {
    System.out.println("\n##teamcity[testFinished name=\'" + escapeName(JUnit4ReflectionUtil.getMethodName(description)) + "\']");
  }

  public void testFailure(Failure failure) throws Exception {
    final String failureMessage = failure.getMessage();
    final String trace = failure.getTrace();
    final Map attrs = new HashMap();
    attrs.put("name", JUnit4ReflectionUtil.getMethodName(failure.getDescription()));
    final ComparisonFailureData notification = createExceptionNotification(failure.getException());
    ComparisonFailureData.registerSMAttributes(notification, trace, failureMessage, attrs);
    System.out.println(ServiceMessage.asString(ServiceMessageTypes.TEST_FAILED, attrs));
  }

  public void testAssumptionFailure(Failure failure) {
    prepareIgnoreMessage(failure.getDescription(), false);
  }

  public synchronized void testIgnored(Description description) throws Exception {
    testStarted(description);
    prepareIgnoreMessage(description, true);
    testFinished(description);
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

  private static void sendTree(Description description, Map groups, PrintStream printStream) {
    if (description.getChildren().isEmpty()) {
      final String methodName = JUnit4ReflectionUtil.getMethodName((Description)description);
      if (methodName != null) {
        printStream.println("##teamcity[suiteTreeNode name=\'" + methodName + "\' " + 
                            getTestMethodLocation(methodName, JUnit4ReflectionUtil.getClassName(description)) + "]");
      }
      return;
    }
    List tests = (List)groups.get(description);
    if (isParameter(description)) {
      tests = description.getChildren();
    }
    if (tests == null) {
      return;
    }
    boolean pass = false;
    for (Iterator iterator = tests.iterator(); iterator.hasNext(); ) {
      final Object next = iterator.next();
      final List childTests = ((Description)next).getChildren();
      final Description nextDescription = (Description)next;
      if ((childTests.isEmpty() && JUnit4ReflectionUtil.getMethodName(nextDescription) != null || isParameter(nextDescription)) && !pass) {
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
        printStream.println("##teamcity[suiteTreeStarted name=\'" + escapeName(getShortName(className)) + "\' locationHint=\'java:suite://" + escapeName(locationHint) + "\']");
      }
      sendTree(nextDescription, groups, printStream);
    }
    if (pass) {
      printStream.println("##teamcity[suiteTreeEnded name=\'" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName((Description)description))) + "\']");
      groups.remove(description);
    }
  }

  private static String getTestMethodLocation(String methodName, String className) {
    return "locationHint=\'java:test://" + escapeName(className + "." + methodName) + "\'";
  }

  private static void groupTests(Object description, Map found) {
    if (!isParameter((Description)description)) {
      final ArrayList childTests = ((Description)description).getChildren();
      List children = (List)found.get(description);
      if (children == null) {
        children = new ArrayList();
        found.put(description, children);
      }
      children.addAll(childTests);
      for (Iterator iterator = childTests.iterator(); iterator.hasNext(); ) {
        groupTests(iterator.next(), found);
      }
    }
  }

  private static boolean isParameter(Description description) {
    String displayName = description.getDisplayName();
    return displayName.startsWith("[") && displayName.endsWith("]");
  }

  public void sendTree(Description description, PrintStream printStream) {
    final List tests = description.getChildren();
    if (tests.isEmpty()) {
      myIgnoreTopSuite = true;
    }
    final HashMap group = new HashMap();
    groupTests(description, group);
    sendTree(description, group, printStream);
  }

  private static String getShortName(String fqName) {
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