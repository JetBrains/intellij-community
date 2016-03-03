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
import java.lang.annotation.Annotation;
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
  private int myFinishedCount = 0;

  public JUnit4TestListener() {
    this(System.out);
  }

  public JUnit4TestListener(PrintStream printStream) {
    myPrintStream = printStream;
    myPrintStream.println("##teamcity[enteredTheMatrix]");
  }

  private static String escapeName(String str) {
    return MapSerializerUtil.escapeStr(str, MapSerializerUtil.STD_ESCAPER);
  }

  public void testRunStarted(Description description) throws Exception {
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
    List parentsHierarchy = parents != null && !parents.isEmpty() ? (List)parents.remove(0) 
                                                                  : Collections.singletonList(Description.createSuiteDescription(classFQN, new Annotation[0]));
    
    final String methodName = getFullMethodName(description, parentsHierarchy.isEmpty() ? null 
                                                                                        : (Description)parentsHierarchy.get(parentsHierarchy.size() - 1));
    if (methodName == null) return;

    int idx = 0;
    Description currentClass;
    Description currentParent;
    while (idx < myStartedSuites.size() && idx < parentsHierarchy.size()) {
      currentClass = (Description)myStartedSuites.get(idx);
      currentParent = (Description)parentsHierarchy.get(parentsHierarchy.size() - 1 - idx);
      if (isHierarchyDifferent(parents, currentClass, currentParent)) {
        break;
      }
      idx++;
    }

    for (int i = myStartedSuites.size() - 1; i >= idx; i--) {
      currentClass = (Description)myStartedSuites.remove(i);
      myFinishedCount = 0;
      myPrintStream.println("##teamcity[testSuiteFinished name=\'" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName(currentClass))) + "\']");
    }

    for (int i = idx; i < parentsHierarchy.size(); i++) {
      final Description descriptionFromHistory = (Description)parentsHierarchy.get(parentsHierarchy.size() - 1 - i);
      final String fqName = JUnit4ReflectionUtil.getClassName(descriptionFromHistory);
      final String className = getShortName(fqName);
      if (!className.equals(myRootName)) {
        myPrintStream.println("##teamcity[testSuiteStarted name=\'" + escapeName(className) + "\'" + (parents == null ? getClassLocation(fqName) : "") + "]");
        myStartedSuites.add(descriptionFromHistory);
      }
    }

    myPrintStream.println("\n##teamcity[testStarted name=\'" + escapeName(methodName) + "\' " + 
                          getTestMethodLocation(methodName, classFQN) + "]");
    myCurrentTestStart = currentTime();
  }

  private static String getClassLocation(String fqName) {
    return " locationHint=\'java:suite://" + escapeName(fqName) + "\'";
  }

  private static boolean isHierarchyDifferent(List parents, 
                                              Description currentClass, 
                                              Description currentParent) {
    if (parents == null) {
      return !currentClass.equals(currentParent);
    }
    else {
      return System.identityHashCode(currentClass) != System.identityHashCode(currentParent);
    }
  }

  protected long currentTime() {
    return System.currentTimeMillis();
  }

  public void testFinished(Description description) throws Exception {
    final String methodName = getFullMethodName(description);
    if (methodName != null) {
      myFinishedCount++;
      final long duration = currentTime() - myCurrentTestStart;
      myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(methodName) +
                            (duration > 0 ? "\' duration=\'"  + Long.toString(duration) : "") + "\']");
    }
  }

  public void testFailure(Failure failure) throws Exception {
    testFailure(failure, failure.getDescription(), MapSerializerUtil.TEST_FAILED, true);
  }

  private void testFailure(Failure failure, Description description, String messageName, boolean local) throws Exception {
    String methodName = getFullMethodName(description);
    if (methodName == null) { //class setUp/tearDown failed
      final boolean isIgnored = MapSerializerUtil.TEST_IGNORED.equals(messageName);
      if (!isIgnored) {
        methodName = "Class Configuration";
        myPrintStream.println("##teamcity[testStarted name=\'" + escapeName(methodName) + "\' " + getClassLocation(JUnit4ReflectionUtil.getClassName(description))+ " ]");
        testFailure(failure, messageName, methodName);
        myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(methodName) + "\']");
      }

      if (myFinishedCount == 0) {
        //only setup failures
        for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext(); ) {
          testFailure(isIgnored ? failure : null, (Description)iterator.next(), MapSerializerUtil.TEST_IGNORED, false);
        }
      }
    }
    else {
      if (!local) testStarted(description);
      testFailure(failure, messageName, methodName);
      if (!local) testFinished(description);
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
      if (failure != null) {
        final String trace = getTrace(failure);
        final Throwable ex = failure.getException();
        final ComparisonFailureData notification = createExceptionNotification(ex);
        ComparisonFailureData.registerSMAttributes(notification, trace, failure.getMessage(), attrs, ex);
      }
    }
    catch (Throwable e) {
      final StringWriter stringWriter = new StringWriter();
      final PrintWriter writer = new PrintWriter(stringWriter);
      e.printStackTrace(writer);
      ComparisonFailureData.registerSMAttributes(null, stringWriter.toString(), e.getMessage(), attrs, e);
    }
    finally {
      myPrintStream.println("\n" + MapSerializerUtil.asString(messageName, attrs));
    }
  }

  protected String getTrace(Failure failure) {
    return failure.getTrace();
  }

  public void testAssumptionFailure(Failure failure) {
    final Description description = failure.getDescription();
    try {
      testFailure(failure, description, MapSerializerUtil.TEST_IGNORED, true);
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

      if (methodName == null && description.getChildren().isEmpty()) {
        methodName = getShortName(description.getDisplayName());
      }

      myMethodNames.put(description, methodName);
    }
    return methodName;
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
      if (methodName != null && parent != null) {
        List parents = (List)myParents.get(description);
        if (parents == null) {
          parents = new ArrayList(1);
          myParents.put(description, parents);
        }
        parents.add(pParents);

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
    myPrintStream.println("##teamcity[treeEnded]");
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