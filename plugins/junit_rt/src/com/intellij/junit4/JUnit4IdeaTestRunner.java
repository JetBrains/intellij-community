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
import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.IDEAJUnitListenerEx;
import com.intellij.rt.execution.junit.IdeaTestRunner;
import org.junit.internal.requests.ClassRequest;
import org.junit.internal.requests.FilterRequest;
import org.junit.runner.*;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** @noinspection UnusedDeclaration*/
public class JUnit4IdeaTestRunner implements IdeaTestRunner {
  private JUnit4TestListener myTestsListener;
  private ArrayList myListeners;

  public void createListeners(ArrayList listeners, int count) {
    myListeners = listeners;
    myTestsListener = new JUnit4TestListener();
  }

  public int startRunnerWithArgs(String[] args, String name, int count, boolean sendTree) {
    try {
      final Request request = JUnit4TestRunnerUtil.buildRequest(args, name, sendTree);
      if (request == null) return -2;

      final Runner testRunner = request.getRunner();
      Description description = getDescription(request, testRunner);
      if (description == null) {
        return -2;
      }

      if (sendTree) {
        do {
          ((JUnit4TestListener)myTestsListener).sendTree(description);
        }
        while (--count > 0);
      }

      final JUnitCore runner = new JUnitCore();
      runner.addListener(myTestsListener);
      for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
        final IDEAJUnitListener junitListener = (IDEAJUnitListener)Class.forName((String)iterator.next()).newInstance();
        runner.addListener(new MyCustomRunListenerWrapper(junitListener, description.getDisplayName()));
      }
      final Result result = runner.run(testRunner);
      return result.wasSuccessful() ? 0 : -1;
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }
  }

  private static Description getDescription(Request request, Runner testRunner) throws NoSuchFieldException, IllegalAccessException {
    Description description = testRunner.getDescription();
    if (description == null) {
      System.err.println("Nothing found to run. Runner " + testRunner.getClass().getName() + " provides no description.");
      return null;
    }
    if (request instanceof ClassRequest) {
      description = getSuiteMethodDescription(request, description);
    }
    else if (request instanceof FilterRequest) {
      description = getFilteredDescription(request, description);
    }
    return description;
  }

  private static Description getFilteredDescription(Request request, Description description) throws NoSuchFieldException, IllegalAccessException {
    Field field;
    try {
      field = FilterRequest.class.getDeclaredField("fFilter");
    }
    catch (NoSuchFieldException e) {
      field = FilterRequest.class.getDeclaredField("filter");
    }
    field.setAccessible(true);
    final Filter filter = (Filter)field.get(request);
    final String filterDescription = filter.describe();
    if (filterDescription != null) {
      boolean isMethodFilter = filterDescription.startsWith("Method");
      if (isMethodFilter && canCompress(description)) return (Description)description.getChildren().get(0);
      try {
        final Description failedTestsDescription = Description.createSuiteDescription(filterDescription, null);
        if (filterDescription.startsWith("Tests") || filterDescription.startsWith("Ignored")) {
          for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext(); ) {
            final Description childDescription = (Description)iterator.next();
            if (filter.shouldRun(childDescription)) {
              failedTestsDescription.addChild(childDescription);
            }
          }
          description = failedTestsDescription;
        } else  if (isMethodFilter && canCompress(failedTestsDescription)) {
          description = (Description)failedTestsDescription.getChildren().get(0);
        }
      }
      catch (NoSuchMethodError e) {
        //junit 4.0 doesn't have method createSuite(String, Annotation...) : skip it
      }
    }
    return description;
  }

  private static boolean canCompress(Description description) {
    return !description.isTest() && description.testCount() == 1;
  }

  private static Description getSuiteMethodDescription(Request request, Description description) throws NoSuchFieldException, IllegalAccessException {
    Field field;
    try {
      field = ClassRequest.class.getDeclaredField("fTestClass");
    }
    catch (NoSuchFieldException e) {
      field = ClassRequest.class.getDeclaredField("testClass");
    }
    field.setAccessible(true);
    final Description methodDescription = Description.createSuiteDescription((Class)field.get(request));
    for (Iterator iterator = description.getChildren().iterator(); iterator.hasNext();) {
      methodDescription.addChild((Description)iterator.next());
    }
    description = methodDescription;
    return description;
  }


  public Object getTestToStart(String[] args, String name) {
    final Request request = JUnit4TestRunnerUtil.buildRequest(args, name, false);
    if (request == null) return null;
    final Runner testRunner = request.getRunner();
    try {
      return getDescription(request, testRunner);
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      System.err.println("Internal Error occurred.");
      e.printStackTrace(System.err);
      return null;
    }
  }

  public List getChildTests(Object description) {
    return ((Description)description).getChildren();
  }

  public String getTestClassName(Object child) {
    return ((Description)child).getClassName();
  }

  public String getStartDescription(Object child) {
    final Description description = (Description)child;
    final String methodName = description.getMethodName();
    return methodName != null ? description.getClassName() + "," + methodName : description.getClassName();
  }

  private static class MyCustomRunListenerWrapper extends RunListener {
    private final IDEAJUnitListener myJunitListener;
    private final String myDisplayName;
    private boolean mySuccess;

    public MyCustomRunListenerWrapper(IDEAJUnitListener junitListener, String displayName) {
      myJunitListener = junitListener;
      myDisplayName = displayName;
    }

    public void testStarted(Description description) throws Exception {
      mySuccess = true;
      myJunitListener.testStarted(JUnit4ReflectionUtil.getClassName(description), JUnit4ReflectionUtil.getMethodName(description));
    }

    public void testFailure(Failure failure) throws Exception {
      mySuccess = ComparisonFailureData.isAssertionError(failure.getException().getClass());
    }

    public void testAssumptionFailure(Failure failure) {
      mySuccess = false;
    }

    public void testIgnored(Description description) throws Exception {
      mySuccess = false;
    }

    public void testFinished(Description description) throws Exception {
      final String className = JUnit4ReflectionUtil.getClassName(description);
      final String methodName = JUnit4ReflectionUtil.getMethodName(description);
      if (myJunitListener instanceof IDEAJUnitListenerEx) {
        ((IDEAJUnitListenerEx)myJunitListener).testFinished(className, methodName, mySuccess);
      } else {
        myJunitListener.testFinished(className, methodName);
      }
    }

    public void testRunStarted(Description description) throws Exception {
      if (myJunitListener instanceof IDEAJUnitListenerEx) {
        ((IDEAJUnitListenerEx)myJunitListener).testRunStarted(description.getDisplayName());
      }
    }

    public void testRunFinished(Result result) throws Exception {
      if (myJunitListener instanceof IDEAJUnitListenerEx) {
        ((IDEAJUnitListenerEx)myJunitListener).testRunFinished(myDisplayName);
      }
    }
  }
}
