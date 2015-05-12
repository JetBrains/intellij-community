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

import com.intellij.rt.execution.junit.*;
import com.intellij.rt.execution.junit.segments.OutputObjectRegistry;
import com.intellij.rt.execution.junit.segments.PacketProcessor;
import org.junit.internal.requests.ClassRequest;
import org.junit.internal.requests.FilterRequest;
import org.junit.runner.*;
import org.junit.runner.manipulation.Filter;
import org.junit.runner.notification.RunListener;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** @noinspection UnusedDeclaration*/
public class JUnit4IdeaTestRunner implements IdeaTestRunner {
  private RunListener myTestsListener;
  private OutputObjectRegistry myRegistry;

  public int startRunnerWithArgs(String[] args, ArrayList listeners, String name, int count, boolean sendTree) {

    final Request request = JUnit4TestRunnerUtil.buildRequest(args, name, sendTree);
    if (request == null) return -1;
    final Runner testRunner = request.getRunner();
    try {
      Description description = testRunner.getDescription();
      if (description == null) {
        System.err.println("Nothing found to run. Runner " + testRunner.getClass().getName() + " provides no description.");
        return -1;
      }
      if (request instanceof ClassRequest) {
        description = getSuiteMethodDescription(request, description);
      }
      else if (request instanceof FilterRequest) {
        description = getFilteredDescription(request, description);
      }

      if (myTestsListener instanceof JUnit4TestListener) {
        if (sendTree) {
          ((JUnit4TestListener)myTestsListener).sendTree(description);
        }
        sendTree = false;
      } else {
        TreeSender.sendTree(this, description, sendTree);
      }
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      System.err.println("Internal Error occured.");
      e.printStackTrace(System.err);
    }

    try {
      final JUnitCore runner = new JUnitCore();
      runner.addListener(myTestsListener);
      for (Iterator iterator = listeners.iterator(); iterator.hasNext();) {
        final IDEAJUnitListener junitListener = (IDEAJUnitListener)Class.forName((String)iterator.next()).newInstance();
        runner.addListener(new RunListener() {
          public void testStarted(Description description) throws Exception {
            junitListener.testStarted(JUnit4ReflectionUtil.getClassName(description), JUnit4ReflectionUtil.getMethodName(description));
          }

          public void testFinished(Description description) throws Exception {
            junitListener.testFinished(JUnit4ReflectionUtil.getClassName(description), JUnit4ReflectionUtil.getMethodName(description));
          }
        });
      }
      long startTime = System.currentTimeMillis();
      Result result;
      if (count == 1) {
        result = runner.run(testRunner);
      }
      else {
        if (count > 0) {
          boolean success = true;
          int i = 0;
          while (i++ < count) {
            result = runner.run(testRunner);
            success &= result.wasSuccessful();
          }
          long endTime = System.currentTimeMillis();
          long runTime = endTime - startTime;
          if (sendTree) new TimeSender(myRegistry).printHeader(runTime);

          return success ? 0 : -1;
        }
        else {
          boolean success = true;
          while (true) {
            result = runner.run(testRunner);
            success &= result.wasSuccessful();
            if (count == -2 && !success) {
              long endTime = System.currentTimeMillis();
              long runTime = endTime - startTime;
              if (sendTree) new TimeSender(myRegistry).printHeader(runTime);
              return -1;
            }
          }
        }
      }
      long endTime = System.currentTimeMillis();
      long runTime = endTime - startTime;
      if (sendTree) new TimeSender(myRegistry).printHeader(runTime);

      if (!result.wasSuccessful()) {
        return -1;
      }
      return 0;
    }
    catch (Exception e) {
      e.printStackTrace(System.err);
      return -2;
    }
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


  public void setStreams(Object segmentedOut, Object segmentedErr, int lastIdx) {
    if (JUnitStarter.SM_RUNNER) {
      myTestsListener = new JUnit4TestListener();
    } else {
      myRegistry = new JUnit4OutputObjectRegistry((PacketProcessor)segmentedOut, lastIdx);
      myTestsListener = new JUnit4TestResultsSender(myRegistry);
    }
  }

  public Object getTestToStart(String[] args, String name) {
    final Request request = JUnit4TestRunnerUtil.buildRequest(args, name, false);
    if (request == null) return null;
    final Runner testRunner = request.getRunner();
    Description description = null;
    try {
      description = testRunner.getDescription();
      if (request instanceof ClassRequest) {
        description = getSuiteMethodDescription(request, description);
      }
      else if (request instanceof FilterRequest) {
        description = getFilteredDescription(request, description);
      }
    }
    catch (Exception e) {
      //noinspection HardCodedStringLiteral
      System.err.println("Internal Error occured.");
      e.printStackTrace(System.err);
    }
    return description;
  }

  public List getChildTests(Object description) {
    return ((Description)description).getChildren();
  }

  public OutputObjectRegistry getRegistry() {
    return myRegistry;
  }

  public String getTestClassName(Object child) {
    return ((Description)child).getClassName();
  }

  public String getStartDescription(Object child) {
    final Description description = (Description)child;
    final String methodName = description.getMethodName();
    return methodName != null ? description.getClassName() + "," + methodName : description.getClassName();
  }
}
