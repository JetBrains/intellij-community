/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.junit5;

import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.IDEAJUnitListenerEx;
import com.intellij.rt.execution.junit.IdeaTestRunner;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class JUnit5IdeaTestRunner implements IdeaTestRunner {
  private TestPlan myTestPlan;
  private JUnit5TestExecutionListener myListener;
  private ArrayList myListeners;
  private Launcher myLauncher;

  @Override
  public void createListeners(ArrayList listeners) {
    myListeners = listeners;
    myListener = new JUnit5TestExecutionListener();
    myLauncher = LauncherFactory.create();
    myLauncher.registerTestExecutionListeners(myListener);
  }

  @Override
  public int startRunnerWithArgs(String[] args, String name, int count, boolean sendTree) {
    try {
      myListener.initialize();
      final String[] packageNameRef = new String[1];
      final LauncherDiscoveryRequest discoveryRequest = JUnit5TestRunnerUtil.buildRequest(args, packageNameRef);
      myTestPlan = myLauncher.discover(discoveryRequest);
      for (Object listenerClassName : myListeners) {
        final IDEAJUnitListener junitListener = (IDEAJUnitListener)Class.forName((String)listenerClassName).newInstance();
        myLauncher.registerTestExecutionListeners(new MyCustomListenerWrapper(junitListener));
      }
      if (sendTree) {
        do {
          myListener.sendTree(myTestPlan, packageNameRef[0]);
        }
        while (--count > 0);
      }

      myLauncher.execute(discoveryRequest);

      return myListener.wasSuccessful() ? 0 : -1;
    }
    catch (Exception e) {
      System.err.println("Internal Error occurred.");
      e.printStackTrace(System.err);
      return -2;
    }
  }

  @Override
  public Object getTestToStart(String[] args, String name) {
    final LauncherDiscoveryRequest discoveryRequest = JUnit5TestRunnerUtil.buildRequest(args, new String[1]);
    Launcher launcher = LauncherFactory.create();
    myTestPlan = launcher.discover(discoveryRequest);
    final Set<TestIdentifier> roots = myTestPlan.getRoots();
    
    return roots.isEmpty() ? null : roots.iterator().next();
  }

  @Override
  public List getChildTests(Object description) {
    return new ArrayList<>(myTestPlan.getChildren((TestIdentifier)description));
  }

  @Override
  public String getStartDescription(Object child) {
    final TestIdentifier testIdentifier = (TestIdentifier)child;
    final String className = JUnit5TestExecutionListener.getClassName(testIdentifier);
    final String methodName = JUnit5TestExecutionListener.getMethodName(testIdentifier);
    if (methodName != null) {
      return className + "#" + methodName;
    }
    return className != null ? className : (testIdentifier).getDisplayName();
  }

  @Override
  public String getTestClassName(Object child) {
    return child.toString();
  }

  private static class MyCustomListenerWrapper implements TestExecutionListener {
    private final IDEAJUnitListener myJunitListener;

    public MyCustomListenerWrapper(IDEAJUnitListener junitListener) {
      myJunitListener = junitListener;}

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
      if (testIdentifier.isTest()) {
        final String className = JUnit5TestExecutionListener.getClassName(testIdentifier);
        final String methodName = JUnit5TestExecutionListener.getMethodName(testIdentifier);
        myJunitListener.testStarted(className, methodName);
      }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
      if (testIdentifier.isTest()) {
        final String className = JUnit5TestExecutionListener.getClassName(testIdentifier);
        final String methodName = JUnit5TestExecutionListener.getMethodName(testIdentifier);
        if (myJunitListener instanceof IDEAJUnitListenerEx) {
          ((IDEAJUnitListenerEx)myJunitListener).testFinished(className, methodName, testExecutionResult.getStatus() == TestExecutionResult.Status.SUCCESSFUL);
        }
        else {
          myJunitListener.testFinished(className, methodName);
        }
      }
    }
  }
}
