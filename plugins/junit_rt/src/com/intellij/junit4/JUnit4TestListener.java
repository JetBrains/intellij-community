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

package com.intellij.junit4;

import com.intellij.rt.execution.junit.ComparisonFailureData;
import com.intellij.rt.execution.junit.MapSerializerUtil;
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
  public static final String EMPTY_SUITE_NAME = "junit.framework.TestSuite$1";
  public static final String EMPTY_SUITE_WARNING = "warning";
  public static final String CLASS_CONFIGURATION = "Class Configuration";

  private List myStartedSuites = new ArrayList();
  private Map   myParents = new HashMap();
  private Map   myMethodNames = new HashMap();
  private final PrintStream myPrintStream;
  private String myRootName;
  private long myCurrentTestStart;

  private Description myCurrentTest;
  private Map myWaitingQueue = new LinkedHashMap();


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

  public void testRunFinished(Result result) {
    try {
      dumpQueue(true);
    }
    finally {
      for (int i = myStartedSuites.size() - 1; i>= 0; i--) {
        Object parent = JUnit4ReflectionUtil.getClassName((Description)myStartedSuites.get(i));
        myPrintStream.println("\n##teamcity[testSuiteFinished name=\'" + escapeName(getShortName((String)parent)) + "\']");
      }
      myStartedSuites.clear();
    }
  }

  public void testStarted(Description description) {
    testStarted(description, null);
  }

  private void testStarted(Description description, String methodName) {
    final List parents = (List)myParents.get(description);
    if (myCurrentTest != null && (parents == null || parents.isEmpty() || !((List)parents.get(0)).contains(myCurrentTest))) {
      if (!myWaitingQueue.containsKey(description)) {
        myWaitingQueue.put(description, new TestEvent());
        return;
      }
    }

    myCurrentTest = description;

    final String classFQN = JUnit4ReflectionUtil.getClassName(description);


    List parentsHierarchy = parents != null && !parents.isEmpty() ? (List)parents.remove(0) 
                                                                  : Collections.singletonList(Description.createSuiteDescription(classFQN, new Annotation[0]));

    if (methodName == null) {
      methodName = getFullMethodName(description, parentsHierarchy.isEmpty() ? null
                                                                             : (Description)parentsHierarchy.get(parentsHierarchy.size() - 1));
      if (methodName == null) return;
    }

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
      myPrintStream.println("\n##teamcity[testSuiteFinished name=\'" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName(currentClass))) + "\']");
    }

    for (int i = idx; i < parentsHierarchy.size(); i++) {
      final Description descriptionFromHistory = (Description)parentsHierarchy.get(parentsHierarchy.size() - 1 - i);
      final String fqName = JUnit4ReflectionUtil.getClassName(descriptionFromHistory);
      final String className = getShortName(fqName);
      if (!className.equals(myRootName)) {
        myPrintStream.println("\n##teamcity[testSuiteStarted name=\'" + escapeName(className) + "\'" + (parents == null ? getClassLocation(fqName) : "") + "]");
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

  public void testFinished(Description description) {
    if (startedInParallel(description)) {
      TestEvent testEvent = (TestEvent)myWaitingQueue.get(description);
      testEvent.setFinished(true);
      return;
    }
    testFinishedNoDumping(getFullMethodName(description));

    dumpQueue(false);
  }

  /**
   * <code>myCurrentTest == null</code> means that initially started test was already finished but no new test were started:
   * test1 started  | myCurrentTest == test1
   * test2 started  | myWaitingQueue contains test2
   * test1 finished | myCurrentTest == null
   * test2 finished | testEvent from myWaitingQueue is marked as finished. Next dumping would dump test2 started/test2 finished
   *
   * <code>myCurrentTest.equals(description)</code> means that finish/failure/ignore event was fired against last started test
   * if in the previous example test2 finishes before test1, then myCurrentTest != description, testEvent in myWaitingQueue needs to be updated
   */
  private boolean startedInParallel(Description description) {
    return myWaitingQueue.containsKey(description) && (myCurrentTest == null || !myCurrentTest.equals(description));
  }

  private void testFinishedNoDumping(final String methodName) {
    if (methodName != null) {
      final long duration = currentTime() - myCurrentTestStart;
      myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(methodName) +
                            (duration > 0 ? "\' duration=\'"  + Long.toString(duration) : "") + "\']");
    }
    myCurrentTest = null;
  }

  public void testFailure(Failure failure) {
    testFailure(failure, failure.getDescription(), MapSerializerUtil.TEST_FAILED);
  }

  private void testFailure(Failure failure, Description description, String messageName) {
    final boolean isIgnored = MapSerializerUtil.TEST_IGNORED.equals(messageName);
    String methodName = getFullMethodName(description);
    if (methodName == null) { //class setUp/tearDown failed
      if (!isIgnored) {
        classConfigurationStarted(description);
        testFailure(failure, description, messageName, CLASS_CONFIGURATION);
        classConfigurationFinished(description);
      }
      if (myStartedSuites.isEmpty() || !description.equals(myStartedSuites.get(myStartedSuites.size() - 1))) {
        for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext(); ) {
          Description next = (Description)iterator.next();
          testStarted(next);
          testFailure(isIgnored ? failure : null, next, MapSerializerUtil.TEST_IGNORED);
          testFinished(next);
        }
      }
    }
    else {
      testFailure(failure, description, messageName, methodName);
    }
  }

  private void classConfigurationFinished(Description description) {
    if (startedInParallel(description)) {
      TestEvent testEvent = (TestEvent)myWaitingQueue.get(description);
      testEvent.setFinished(true);
      return;
    }

    myPrintStream.println("\n##teamcity[testFinished name=\'" + escapeName(CLASS_CONFIGURATION) + "\']");
    myCurrentTest = null;
  }

  private void classConfigurationStarted(Description description) {
    if (myCurrentTest != null) {
      TestEvent value = new TestEvent();
      value.setMethodName(CLASS_CONFIGURATION);
      myWaitingQueue.put(description, value);
      return;
    }

    myCurrentTest = description;
    myPrintStream.println("\n##teamcity[testStarted name=\'" + escapeName(CLASS_CONFIGURATION) + "\' " + getClassLocation(JUnit4ReflectionUtil.getClassName(description)) + " ]");
  }

  private void testFailure(Failure failure, Description description, String messageName, String methodName) {
    final boolean isIgnored = MapSerializerUtil.TEST_IGNORED.equals(messageName);
    if (startedInParallel(description)) {
      TestEvent testEvent = (TestEvent)myWaitingQueue.get(description);
      if (testEvent == null) {
        testEvent = new TestEvent();
        myWaitingQueue.put(description, testEvent);
      }
      testEvent.setIgnored(isIgnored);
      testEvent.setFailure(failure);
      return;
    }

    final Map attrs = new LinkedHashMap();
    attrs.put("name", methodName);
    final long duration = currentTime() - myCurrentTestStart;
    if (duration > 0) {
      attrs.put("duration", Long.toString(duration));
    }
    try {
      if (failure != null) {
        final String trace = getTrace(failure);
        final Throwable ex = failure.getException();
        final ComparisonFailureData notification = ExpectedPatterns.createExceptionNotification(ex);
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
    testFailure(failure, failure.getDescription(), MapSerializerUtil.TEST_IGNORED);
  }

  private String getFullMethodName(Description description) {
    return getFullMethodName(description, null);
  }

  private String getFullMethodName(Description description, Description parent) {
    return getFullMethodName(description, parent, false);
  }

  private String getFullMethodName(Description description,
                                   Description parent,
                                   boolean acceptNull) {
    String methodName = (String)myMethodNames.get(description);
    if (methodName == null) {
      methodName = JUnit4ReflectionUtil.getMethodName(description);
      if (methodName != null && (parent == null || !isParameter(parent))) {
        String shortName = getShortName(JUnit4ReflectionUtil.getClassName(description));
        methodName = shortName.length() == 0 ?  methodName : shortName + "." + methodName;
      }

      if (!acceptNull && methodName == null && description.getChildren().isEmpty()) {
        methodName = getShortName(description.getDisplayName());
      }

      myMethodNames.put(description, methodName);
    }
    return methodName;
  }
  
  public void testIgnored(Description description) {
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

  private void testIgnored(Description description, String methodName) {
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

    if (startedInParallel(description)) {
      TestEvent testEvent = (TestEvent)myWaitingQueue.get(description);
      if (testEvent == null) {
        testEvent = new TestEvent();
        myWaitingQueue.put(description, testEvent);
      }
      testEvent.setIgnored(true);
      testEvent.setAttrs(attrs);
    }
    else {
      myPrintStream.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_IGNORED, attrs));
    }
    testFinished(description);
  }

  private void dumpQueue(boolean acceptUnfinished) {
    for (Iterator iterator = myWaitingQueue.keySet().iterator(); iterator.hasNext(); ) {
      Description description = (Description)iterator.next();
      TestEvent testEvent = (TestEvent)myWaitingQueue.get(description);
      if (acceptUnfinished || testEvent.isFinished()) {
        testStarted(description, testEvent.getMethodName());

        iterator.remove();

        Failure failure = testEvent.getFailure();
        if (testEvent.isIgnored()) {
          Map attrs = testEvent.getAttrs();
          if (attrs == null) {
            testFailure(failure, description, MapSerializerUtil.TEST_IGNORED);
          }
          else {
            myPrintStream.println(MapSerializerUtil.asString(MapSerializerUtil.TEST_IGNORED, attrs));
          }
        }
        else if (failure != null) {
          testFailure(failure);
        }

        final String methodName = testEvent.getMethodName();
        testFinishedNoDumping(methodName != null ? methodName : getFullMethodName(description));
      }
    }
  }

  private static class TestEvent {
    private Failure myFailure;
    private boolean myIgnored;
    private boolean myFinished;
    private Map myAttrs;
    private String myMethodName;

    public Failure getFailure() {
      return myFailure;
    }

    public boolean isIgnored() {
      return myIgnored;
    }

    public boolean isFinished() {
      return myFinished;
    }

    public void setFinished(boolean finished) {
      myFinished = finished;
    }

    public void setFailure(Failure failure) {
      myFailure = failure;
    }

    public void setIgnored(boolean ignored) {
      myIgnored = ignored;
    }

    public void setAttrs(Map attrs) {
      myAttrs = attrs;
    }

    public Map getAttrs() {
      return myAttrs;
    }

    public void setMethodName(String methodName) {
      myMethodName = methodName;
    }

    public String getMethodName() {
      return myMethodName;
    }
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

    List parents = (List)myParents.get(description);
    if (parents == null) {
      parents = new ArrayList(1);
      myParents.put(description, parents);
    }
    parents.add(pParents);

    String className = JUnit4ReflectionUtil.getClassName(description);
    if (description.isTest()) {
      final String methodName = getFullMethodName((Description)description, parent, true);
      if (methodName != null ) {
        if (isWarning(methodName, className) && parent != null) {
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