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
import com.intellij.rt.execution.junit.MapSerializerUtil;
import junit.framework.ComparisonFailure;
import org.junit.Ignore;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;

public class JUnit4TestListener extends RunListener {
  private static final String MESSAGE_LENGTH_FOR_PATTERN_MATCHING = "idea.junit.message.length.threshold";
  private static final String JUNIT_FRAMEWORK_COMPARISON_NAME = ComparisonFailure.class.getName();
  private static final String ORG_JUNIT_COMPARISON_NAME = "org.junit.ComparisonFailure";
  public static final String EMPTY_SUITE_NAME = "junit.framework.TestSuite$1";
  public static final String EMPTY_SUITE_WARNING = "warning";

  private List myStartedSuites = new ArrayList();
  private Map   myParents = new HashMap();
  private Map   myMethodNames = new HashMap();
  private final PrintStream myPrintStream;
  private String myRootName;
  private long myCurrentTestStart;

  public JUnit4TestListener() {
    myPrintStream = System.out;
  }

  public JUnit4TestListener(PrintStream printStream) {
    myPrintStream = printStream;
  }

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  public void testRunStarted(Description description) throws Exception {
    myPrintStream.println("##teamcity[enteredTheMatrix]");
    if (myRootName != null && !myRootName.startsWith("[")) {
      int lastPointIdx = myRootName.lastIndexOf('.');
      String name = myRootName;
      String comment = null;
      if (lastPointIdx >= 0) {
        name = myRootName.substring(lastPointIdx + 1);
        comment = myRootName.substring(0, lastPointIdx);
      }

      myPrintStream.println("##teamcity[rootName name = \'" + escapeName(name) + 
                            (comment != null ? ("\' comment = \'" + escapeName(comment)) : "") + "\'" +
                            " location = \'java:suite://" + escapeName(myRootName) +
                            "\']");
      myRootName = getShortName(myRootName);
    }
  }

  public void testRunFinished(Result result) throws Exception {
    for (int i = myStartedSuites.size() - 1; i>= 0; i--) {
      Object parent = JUnit4ReflectionUtil.getClassName((Description)myStartedSuites.get(i));
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(getShortName((String)parent)) + "\']");
    }
    myStartedSuites.clear();
  }

  public void testStarted(Description description) throws Exception {
    final String classFQN = JUnit4ReflectionUtil.getClassName(description);

    final List parents = (List)myParents.get(description);
    List parentsHierarchy = parents != null && !parents.isEmpty() ? (List)parents.remove(0) : Collections.singletonList(description);
    
    final String methodName = getFullMethodName(description, parentsHierarchy.isEmpty() ? null 
                                                                                        : (Description)parentsHierarchy.get(parentsHierarchy.size() - 1));
    if (methodName == null) return;

    int idx = 0;
    Description currentClass;
    Description currentParent;
    while (idx < myStartedSuites.size() && idx < parentsHierarchy.size()) {
      currentClass = (Description)myStartedSuites.get(idx);
      currentParent = (Description)parentsHierarchy.get(parentsHierarchy.size() - 1 - idx);
      if (System.identityHashCode(currentClass) != System.identityHashCode(currentParent)) break;
      idx++;
    }

    for (int i = myStartedSuites.size() - 1; i >= idx; i--) {
      currentClass = (Description)myStartedSuites.remove(i);
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName(currentClass))) + "\']");
    }

    for (int i = idx; i < parentsHierarchy.size(); i++) {
      final Description descriptionFromHistory = (Description)parentsHierarchy.get(parentsHierarchy.size() - 1 - i);
      final String fqName = JUnit4ReflectionUtil.getClassName(descriptionFromHistory);
      final String className = getShortName(fqName);
      if (!className.equals(myRootName)) {
        myPrintStream.println("##teamcity[testSuiteStarted name=\'" + escapeName(className) + "\'" + (parents == null ? " locationHint=\'java:suite://" + escapeName(fqName) + "\'" : "") + "]");
        myStartedSuites.add(descriptionFromHistory);
      }
    }

    myPrintStream.println("##teamcity[testStarted name=\'" + escapeName(methodName) + "\' " + 
                          getTestMethodLocation(methodName, classFQN) + "]");
    myCurrentTestStart = currentTime();
  }

  protected long currentTime() {
    return System.currentTimeMillis();
  }

  public void testFinished(Description description) throws Exception {
    final String methodName = getFullMethodName(description);
    if (methodName != null) {
      final long duration = currentTime() - myCurrentTestStart;
      myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(methodName) +
                            (duration > 0 ? "\' duration=\'"  + Long.toString(duration) : "") + "\']");
    }
  }

  public void testFailure(Failure failure) throws Exception {
    final Description description = failure.getDescription();
    final String methodName = getFullMethodName(description);
    //class setUp failed
    if (methodName == null) {
      for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext(); ) {
        testFailure(failure, MapSerializerUtil.TEST_FAILED, getFullMethodName((Description)iterator.next()));
      }
    }
    else {
      testFailure(failure, MapSerializerUtil.TEST_FAILED, methodName);
    }
  }

  private void testFailure(Failure failure, String messageName, String methodName) {
    final Map attrs = new HashMap();
    attrs.put("name", methodName);
    final long duration = currentTime() - myCurrentTestStart;
    if (duration > 0) {
      attrs.put("duration", Long.toString(duration));
    }
    try {
      final String trace = getTrace(failure);
      final Throwable ex = failure.getException();
      final ComparisonFailureData notification = createExceptionNotification(ex);
      ComparisonFailureData.registerSMAttributes(notification, trace, failure.getMessage(), attrs, ex);
    }
    catch (Throwable e) {
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter writer = new PrintWriter(stringWriter);
      e.printStackTrace(writer);
      ComparisonFailureData.registerSMAttributes(null, stringWriter.toString(), e.getMessage(), attrs, e);
    }
    finally {
      myPrintStream.println(MapSerializerUtil.asString(messageName, attrs));
    }
  }

  protected String getTrace(Failure failure) {
    return failure.getTrace();
  }

  public void testAssumptionFailure(Failure failure) {
    final Description description = failure.getDescription();
    try {
      final String methodName = getFullMethodName(description);
      //class setUp failed
      if (methodName == null) {
        for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext(); ) {
          final Description testDescription = (Description)iterator.next();
          testAssumptionFailure(failure, testDescription, getFullMethodName(testDescription));
        }
      }
      else {
        testAssumptionFailure(failure, description, methodName);
      }
    }
    catch (Exception ignore) {}
  }

  private String getFullMethodName(Description description) {
    return getFullMethodName(description, null);
  }

  private String getFullMethodName(Description description, Description parent) {
    String methodName = (String)myMethodNames.get(description);
    if (methodName == null) {
      methodName = JUnit4ReflectionUtil.getMethodName(description);
      if (methodName != null && (parent == null || !isParameter(parent))) {
        methodName = getShortName(JUnit4ReflectionUtil.getClassName(description)) + "." + methodName;
      }
      myMethodNames.put(description, methodName);
    }
    return methodName;
  }

  private void testAssumptionFailure(Failure failure, Description testDescription, String name) throws Exception {
    testStarted(testDescription);
    testFailure(failure, MapSerializerUtil.TEST_IGNORED, name);
    testFinished(testDescription);
  }

  public synchronized void testIgnored(Description description) throws Exception {
    final String methodName = getFullMethodName(description);
    if (methodName == null) {
      for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext(); ) {
        final Description testDescription = (Description)iterator.next();
        testIgnored(testDescription, getFullMethodName(testDescription));
      }
    }
    else {
      testIgnored(description, methodName);
    }
  }

  private void testIgnored(Description description, String methodName) throws Exception {
    testStarted(description);
    Map attrs = new HashMap();
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
    attrs.put("name", methodName);
    myPrintStream.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_IGNORED, attrs));
    testFinished(description);
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
        return ComparisonFailureData.create(cause);
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

  private void sendTree(Description description, Description parent, List currentParents) {
    List pParents = new ArrayList(3);
    pParents.addAll(currentParents);
    if (parent != null) {
      final String parentClassName = JUnit4ReflectionUtil.getClassName(parent);
      if (!myRootName.equals(parentClassName)) {
        pParents.add(0, parent);
      }
    }

    String className = JUnit4ReflectionUtil.getClassName(description);
    if (description.getChildren().isEmpty()) {
      final String methodName = getFullMethodName((Description)description, parent);
      if (methodName != null) {
        if (parent != null) {
          List parents = (List)myParents.get(description);
          if (parents == null) {
            parents = new ArrayList(1);
            myParents.put(description, parents);
          }
          parents.add(pParents);
        }
        if (isWarning(methodName, className)) {
          className = JUnit4ReflectionUtil.getClassName(parent);
        }
        myPrintStream.println("##teamcity[suiteTreeNode name=\'" + escapeName(methodName) + "\' " + getTestMethodLocation(methodName, className) + "]");
      }

      return;
    }
   
    List tests = description.getChildren();
    boolean pass = false;
    for (Iterator iterator = tests.iterator(); iterator.hasNext(); ) {
      final Object next = iterator.next();
      final Description nextDescription = (Description)next;
      if ((myRootName == null || !myRootName.equals(className)) && !pass) {
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
      sendTree(nextDescription, description, pParents);
    }
    if (pass) {
      myPrintStream.println("##teamcity[suiteTreeEnded name=\'" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName((Description)description))) + "\']");
    }
  }

  private static boolean isWarning(String methodName, String className) {
    return EMPTY_SUITE_WARNING.equals(methodName) && EMPTY_SUITE_NAME.equals(className);
  }

  private static String getTestMethodLocation(String methodName, String className) {
    return "locationHint=\'java:test://" + escapeName(className + "." + getShortName(methodName)) + "\'";
  }

  private static boolean isParameter(Description description) {
    String displayName = description.getDisplayName();
    return displayName.startsWith("[") && displayName.endsWith("]");
  }

  public void sendTree(Description description) {
    myRootName = JUnit4ReflectionUtil.getClassName((Description)description);
    sendTree(description, null, new ArrayList());
  }

  private static String getShortName(String fqName) {
    if (fqName == null) return null;
    final int idx = fqName.indexOf("[");
    if (idx == 0) {
      //param name
      return fqName;
    }
    int lastPointIdx = fqName.lastIndexOf('.');
    if (idx > 0 && fqName.endsWith("]")) {
      lastPointIdx = fqName.substring(0, idx).lastIndexOf('.');
    }
    if (lastPointIdx >= 0) {
      return fqName.substring(lastPointIdx + 1);
    }
    return fqName;
  }
}