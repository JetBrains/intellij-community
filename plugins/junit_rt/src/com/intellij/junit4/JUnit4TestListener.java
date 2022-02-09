// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.junit4;

import com.intellij.rt.execution.TestListenerProtocol;
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
import java.util.*;

public class JUnit4TestListener extends RunListener {
  public static final String EMPTY_SUITE_NAME = "junit.framework.TestSuite$1";
  public static final String EMPTY_SUITE_WARNING = "warning";

  private final List<Description> myStartedSuites = new ArrayList<Description>();
  private final Map<Description, List<List<Description>>> myParents = new HashMap<Description, List<List<Description>>>();
  private final Map<Description, String> myMethodNames = new HashMap<Description, String>();
  private final PrintStream myPrintStream;
  private String myRootName;
  private long myCurrentTestStart;

  private Description myCurrentTest;
  private final Map<Description, TestEvent> myWaitingQueue = new LinkedHashMap<Description, TestEvent>();
  private static final JUnitTestTreeNodeManager NODE_NAMES_MANAGER = getTestTreeNodeManager();


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

  @Override
  public void testRunStarted(Description description) {
    if (myRootName != null && !myRootName.equals("null") && !myRootName.startsWith("[")) {
      JUnitTestTreeNodeManager.TestNodePresentation rootNodePresentation = NODE_NAMES_MANAGER.getRootNodePresentation(myRootName);

      myPrintStream.println("##teamcity[rootName name = '" + escapeName(rootNodePresentation.getName()) +
                            (rootNodePresentation.getComment() != null ? ("' comment = '" + escapeName(rootNodePresentation.getComment())) : "") +
                            "' location = 'java:suite://" + escapeName(myRootName) + "']");
      myRootName = getShortName(myRootName);
    }
  }

  @Override
  public void testRunFinished(Result result) {
    try {
      dumpQueue(true);
    }
    finally {
      for (int i = myStartedSuites.size() - 1; i>= 0; i--) {
        String className = JUnit4ReflectionUtil.getClassName(myStartedSuites.get(i));
        if (!className.equals(myRootName)) {
          myPrintStream.println("##teamcity[testSuiteFinished name='" + escapeName(getShortName(className)) + "']");
        }
      }
      myStartedSuites.clear();
    }
  }

  @Override
  public void testStarted(Description description) {
    testStarted(description, null);
  }

  private void testStarted(Description description, String methodName) {
    final List<List<Description>> parents = myParents.get(description);
    if (myCurrentTest != null && (parents == null || parents.isEmpty() || !parents.get(0).contains(myCurrentTest))) {
      if (!myWaitingQueue.containsKey(description)) {
        myWaitingQueue.put(description, new TestEvent());
        return;
      }
    }

    myCurrentTest = description;

    final String classFQN = JUnit4ReflectionUtil.getClassName(description);


    List<Description> parentsHierarchy = new ArrayList<Description>();
    if (parents != null && !parents.isEmpty()) {
      parentsHierarchy = parents.remove(0);
    }

    if (parentsHierarchy.isEmpty()) {
      parentsHierarchy = Collections.singletonList(Description.createSuiteDescription(classFQN));
    }

    if (methodName == null) {
      methodName = getFullMethodName(description, parentsHierarchy.isEmpty() ? null
                                                                             : parentsHierarchy.get(parentsHierarchy.size() - 1));
      if (methodName == null) return;
    }

    int idx = 0;
    Description currentClass;
    Description currentParent;
    while (idx < myStartedSuites.size() && idx < parentsHierarchy.size()) {
      currentClass = myStartedSuites.get(idx);
      currentParent = parentsHierarchy.get(parentsHierarchy.size() - 1 - idx);
      if (isHierarchyDifferent(parents, currentClass, currentParent)) {
        break;
      }
      idx++;
    }

    for (int i = myStartedSuites.size() - 1; i >= idx; i--) {
      currentClass = myStartedSuites.remove(i);
      myPrintStream.println(
        "##teamcity[testSuiteFinished name='" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName(currentClass))) + "']");
    }

    for (int i = idx; i < parentsHierarchy.size(); i++) {
      final Description descriptionFromHistory = parentsHierarchy.get(parentsHierarchy.size() - 1 - i);
      final String fqName = JUnit4ReflectionUtil.getClassName(descriptionFromHistory);
      final String className = getShortName(fqName);
      if (!className.equals(myRootName)) {
        myPrintStream.println("##teamcity[testSuiteStarted name='" + escapeName(className) +
                              "'" + getSuiteLocation(descriptionFromHistory, description, fqName) + "]");
      }
      myStartedSuites.add(descriptionFromHistory);
    }

    myPrintStream.println("##teamcity[testStarted name='" + escapeName(methodName.replaceFirst("/", ".")) + "' " +
                          NODE_NAMES_MANAGER.getTestLocation(description, classFQN, methodName) + "]");
    myCurrentTestStart = currentTime();
  }

  private static boolean isHierarchyDifferent(List<?> parents, 
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

  @Override
  public void testFinished(Description description) {
    if (startedInParallel(description)) {
      TestEvent testEvent = myWaitingQueue.get(description);
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
      myPrintStream.println("##teamcity[testFinished name='" + escapeName(methodName.replaceFirst("/", ".")) +
                            (duration > 0 ? "' duration='" + duration : "") + "']");
    }
    myCurrentTest = null;
  }

  @Override
  public void testFailure(Failure failure) {
    testFailure(failure, failure.getDescription(), MapSerializerUtil.TEST_FAILED);
  }

  private void testFailure(Failure failure, Description description, String messageName) {
    final boolean isIgnored = MapSerializerUtil.TEST_IGNORED.equals(messageName);
    String methodName = getFullMethodName(description);
    if (methodName == null) { //class setUp/tearDown failed
      if (!isIgnored) {
        classConfigurationStarted(description);
        testFailure(failure, description, messageName, TestListenerProtocol.CLASS_CONFIGURATION);
        classConfigurationFinished(description);
      }
      if (myStartedSuites.isEmpty() || !description.equals(myStartedSuites.get(myStartedSuites.size() - 1))) {
        for (Description next : description.getChildren()) {
          testStarted(next);
          testFailure(isIgnored ? failure : null, next, MapSerializerUtil.TEST_IGNORED);
          testFinished(next);
        }
      }
    }
    else {
      testFailure(failure, description, messageName, methodName.replaceFirst("/", "."));
    }
  }

  private void classConfigurationFinished(Description description) {
    if (startedInParallel(description)) {
      TestEvent testEvent = myWaitingQueue.get(description);
      testEvent.setFinished(true);
      return;
    }

    myPrintStream.println("##teamcity[testFinished name='" + escapeName(TestListenerProtocol.CLASS_CONFIGURATION) + "']");
    myCurrentTest = null;
  }

  private void classConfigurationStarted(Description description) {
    if (myCurrentTest != null) {
      TestEvent value = new TestEvent();
      value.setMethodName(TestListenerProtocol.CLASS_CONFIGURATION);
      myWaitingQueue.put(description, value);
      return;
    }

    myCurrentTest = description;
    myPrintStream.println("##teamcity[testStarted name='" + escapeName(TestListenerProtocol.CLASS_CONFIGURATION) +
                          "'" + getSuiteLocation(JUnit4ReflectionUtil.getClassName(description)) + " ]");
  }

  private void testFailure(Failure failure, Description description, String messageName, String methodName) {
    final boolean isIgnored = MapSerializerUtil.TEST_IGNORED.equals(messageName);
    if (startedInParallel(description)) {
      TestEvent testEvent = myWaitingQueue.get(description);
      if (testEvent == null) {
        testEvent = new TestEvent();
        myWaitingQueue.put(description, testEvent);
      }
      testEvent.setIgnored(isIgnored);
      testEvent.setFailure(failure);
      return;
    }

    Throwable ex = failure != null ? failure.getException() : null;
    if (ex != null && isMultipleFailuresError(ex.getClass())) {
      try {
        Object failures = Class.forName("org.opentest4j.MultipleFailuresError").getDeclaredMethod("getFailures").invoke(ex);
        //noinspection unchecked
        for (Throwable throwable : (List<Throwable>)failures) {
          testFailure(new Failure(description, throwable), description, messageName, methodName);
        }
        return;
      }
      catch (Throwable ignore) { }
    }

    final Map<String, String> attrs = new LinkedHashMap<String, String>();
    attrs.put("name", methodName);
    final long duration = currentTime() - myCurrentTestStart;
    if (duration > 0) {
      attrs.put("duration", Long.toString(duration));
    }
    try {
      if (failure != null) {
        final String trace = getTrace(failure);
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
      myPrintStream.println(MapSerializerUtil.asString(messageName, attrs));
    }
  }

  private static boolean isMultipleFailuresError(Class<?> aClass) {
    if (aClass.getName().equals("org.opentest4j.MultipleFailuresError")) {
      return true;
    }

    Class<?> superclass = aClass.getSuperclass();
    return superclass != null && isMultipleFailuresError(superclass);
  }

  protected String getTrace(Failure failure) {
    return failure.getTrace();
  }

  @Override
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
    String methodName = myMethodNames.get(description);
    if (methodName == null) {
      methodName = JUnit4ReflectionUtil.getMethodName(description);
      if (methodName != null && (parent == null || !isParameter(parent))) {
        String shortName = getShortName(JUnit4ReflectionUtil.getClassName(description));
        methodName = shortName.length() == 0 ?  methodName : shortName + "/" + methodName;
      }

      if (!acceptNull && methodName == null && description.getChildren().isEmpty()) {
        methodName = getShortName(description.getDisplayName());
      }

      myMethodNames.put(description, methodName);
    }
    return methodName;
  }
  
  @Override
  public void testIgnored(Description description) {
    final String methodName = getFullMethodName(description);
    if (methodName == null) {
      for (final Description testDescription : description.getChildren()) {
        testIgnored(testDescription, getFullMethodName(testDescription));//todo
      }
    }
    else {
      testIgnored(description, methodName.replaceFirst("/", "."));
    }
  }

  private void testIgnored(Description description, String methodName) {
    testStarted(description);
    Map<String, String> attrs = new HashMap<String, String>();
    try {
      final Ignore ignoredAnnotation = description.getAnnotation(Ignore.class);
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
      TestEvent testEvent = myWaitingQueue.get(description);
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
    for (Iterator<Description> iterator = myWaitingQueue.keySet().iterator(); iterator.hasNext(); ) {
      Description description = iterator.next();
      TestEvent testEvent = myWaitingQueue.get(description);
      if (acceptUnfinished || testEvent.isFinished()) {
        testStarted(description, testEvent.getMethodName());

        iterator.remove();

        Failure failure = testEvent.getFailure();
        if (testEvent.isIgnored()) {
          Map<String, String> attrs = testEvent.getAttrs();
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
    private Map<String, String> myAttrs;
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

    public void setAttrs(Map<String, String> attrs) {
      myAttrs = attrs;
    }

    public Map<String, String> getAttrs() {
      return myAttrs;
    }

    public void setMethodName(String methodName) {
      myMethodName = methodName;
    }

    public String getMethodName() {
      return myMethodName;
    }
  }

  private void sendTree(Description description, Description parent, List<Description> currentParents) {
    List<Description> pParents = new ArrayList<Description>(3);
    pParents.addAll(currentParents);
    if (parent != null) {
      final String parentClassName = JUnit4ReflectionUtil.getClassName(parent);
      if (!myRootName.equals(parentClassName)) {
        pParents.add(0, parent);
      }
    }

    List<List<Description>> parents = myParents.get(description);
    if (parents == null) {
      parents = new ArrayList<List<Description>>(1);
      myParents.put(description, parents);
    }
    parents.add(pParents);

    String className = JUnit4ReflectionUtil.getClassName(description);
    if (description.isTest()) {
      final String methodName = getFullMethodName(description, parent, true);
      if (methodName != null ) {
        if (isWarning(methodName, className) && parent != null) {
          className = JUnit4ReflectionUtil.getClassName(parent);
        }
        myPrintStream.println("##teamcity[suiteTreeNode name='" + escapeName(methodName.replaceFirst("/", ".")) +
                              "' " + NODE_NAMES_MANAGER.getTestLocation(description, className, methodName) + "]");
      }
      else {
        myPrintStream.println("##teamcity[suiteTreeStarted name='" + escapeName(getShortName(className)) +
                              "' locationHint='java:suite://" + escapeName(className) + "']");
        myPrintStream.println("##teamcity[suiteTreeEnded name='" + escapeName(getShortName(className)) + "']");
      }
      return;
    }
   
    List<Description> tests = description.getChildren();
    boolean pass = false;
    for (final Description nextDescription : tests) {
      if ((myRootName == null || !myRootName.equals(className)) && !pass) {
        pass = true;
        myPrintStream.println("##teamcity[suiteTreeStarted name='" + escapeName(getShortName(className)) + "'" +
                              getSuiteLocation(description, nextDescription, className) + "]");
      }
      sendTree(nextDescription, description, pParents);
    }
    if (pass) {
      myPrintStream.println(
        "##teamcity[suiteTreeEnded name='" + escapeName(getShortName(JUnit4ReflectionUtil.getClassName(description))) + "']");
    }
  }

  private static String getSuiteLocation(Description parentDescription, Description description, String parentClassName) {
    String locationHint = parentClassName;
    if (isParameter(parentDescription)) {
      final String displayName = description.getDisplayName();
      final int paramIdx = displayName.indexOf(locationHint);
      if (paramIdx > -1) {
        locationHint = displayName.substring(paramIdx + locationHint.length());
        if (locationHint.startsWith("(") && locationHint.endsWith(")")) {
          locationHint = locationHint.substring(1, locationHint.length() - 1) + "." + parentClassName; 
        }
      }
    }
    return getSuiteLocation(locationHint);
  }

  private static String getSuiteLocation(String locationHint) {
    return " locationHint='java:suite://" + escapeName(locationHint) + "'";
  }

  private static boolean isWarning(String methodName, String className) {
    return EMPTY_SUITE_WARNING.equals(methodName) && EMPTY_SUITE_NAME.equals(className);
  }

  private static boolean isParameter(Description description) {
    String displayName = description.getDisplayName();
    return displayName.startsWith("[") && displayName.endsWith("]");
  }

  public void sendTree(Description description) {
    myRootName = JUnit4ReflectionUtil.getClassName(description);
    sendTree(description, null, new ArrayList<Description>());
    myPrintStream.println("##teamcity[treeEnded]");
  }

  private static String getShortName(String fqName) {
    return getShortName(fqName, false);
  }

  private static String getShortName(String fqName, boolean splitBySlash) {
    return NODE_NAMES_MANAGER.getNodeName(fqName, splitBySlash);
  }

  private static JUnitTestTreeNodeManager getTestTreeNodeManager() {
    String junitNodeNamesManagerClassName = System.getProperty(JUnitTestTreeNodeManager.JUNIT_TEST_TREE_NODE_MANAGER_ARGUMENT);

    JUnitTestTreeNodeManager result = JUnitTestTreeNodeManager.JAVA_NODE_NAMES_MANAGER;
    if (junitNodeNamesManagerClassName != null) {
      try {
        Class<? extends JUnitTestTreeNodeManager> junitNodeNamesManagerClass = Class.forName(junitNodeNamesManagerClassName)
          .asSubclass(JUnitTestTreeNodeManager.class);
        result = junitNodeNamesManagerClass.newInstance();
      }
      catch (ClassCastException ignored) {
      }
      catch (IllegalAccessException ignored) {
      }
      catch (InstantiationException ignored) {
      }
      catch (ClassNotFoundException ignored) {
      }
    }
    return result;
  }
}