// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.junit5;

import com.intellij.rt.execution.junit.IDEAJUnitListener;
import com.intellij.rt.execution.junit.IDEAJUnitListenerEx;
import com.intellij.rt.junit.IdeaTestRunner;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.EngineDescriptor;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.LauncherFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class JUnit5IdeaTestRunner implements IdeaTestRunner<TestIdentifier> {
  private final List<JUnit5TestExecutionListener> myExecutionListeners = new ArrayList<>();
  private ArrayList<String> myListeners;
  private Launcher myLauncher;

  public JUnit5IdeaTestRunner() {
    Runnable warmup = (Runnable) Proxy.newProxyInstance(
      JUnit5IdeaTestRunner.class.getClassLoader(),
      new Class<?>[]{Runnable.class},
      (proxy, method, args) -> null);
    warmup.run();
  }

  @Override
  public void createListeners(ArrayList<String> listeners, int count) {
    myListeners = listeners;
    do {
      JUnit5TestExecutionListener currentListener = new JUnit5TestExecutionListener();
      myExecutionListeners.add(currentListener);
      if (count > 2) {
        currentListener.initializeIdSuffix(count);
      }
    }
    while (--count > 0);
    myLauncher = LauncherFactory.create();
  }

  @Override
  public int startRunnerWithArgs(String[] args, String programParam, int count, boolean sendTree) {
    try {
      JUnit5TestExecutionListener listener = myExecutionListeners.get(0);
      listener.initializeIdSuffix(!sendTree);
      final String[] packageNameRef = new String[1];
      final LauncherDiscoveryRequest discoveryRequest = JUnit5TestRunnerUtil.buildRequest(args, packageNameRef, programParam);
      List<TestExecutionListener> listeners = new ArrayList<>();
      listeners.add(listener);
      for (String listenerClassName : myListeners) {
        final IDEAJUnitListener junitListener = (IDEAJUnitListener)Class.forName(listenerClassName).newInstance();
        listeners.add(new MyCustomListenerWrapper(junitListener));
      }
      if (sendTree) {
        for (JUnit5TestExecutionListener executionListener : myExecutionListeners) {
          executionListener.setRootName(packageNameRef[0]);
          executionListener.setSendTree();
        }
      }

      myLauncher.execute(discoveryRequest, listeners.toArray(new TestExecutionListener[0]));

      return listener.wasSuccessful() ? 0 : -1;
    }
    catch (Exception e) {
      System.err.println("Internal Error occurred.");
      e.printStackTrace(System.err);
      return -2;
    }
    finally {
      if (count > 0) myExecutionListeners.remove(0);
    }
  }

  private TestPlan myForkedTestPlan;
  private static final TestIdentifier FAKE_ROOT = TestIdentifier.from(new EngineDescriptor(UniqueId.forEngine("FAKE_ENGINE"), "FAKE ENGINE"));
  @Override
  public TestIdentifier getTestToStart(String[] args, String name) {
    final LauncherDiscoveryRequest discoveryRequest = JUnit5TestRunnerUtil.buildRequest(args, new String[1], "");
    myForkedTestPlan = LauncherFactory.create().discover(discoveryRequest);
    final Set<TestIdentifier> roots = myForkedTestPlan.getRoots();
    if (roots.isEmpty()) return null;
    List<TestIdentifier> nonEmptyRoots = roots.stream()
      .filter(identifier -> !myForkedTestPlan.getChildren(identifier).isEmpty())
      .collect(Collectors.toList());
    if (nonEmptyRoots.isEmpty()) return null;
    return nonEmptyRoots.size() == 1 ? nonEmptyRoots.get(0) : FAKE_ROOT;
  }

  @Override
  public List<TestIdentifier> getChildTests(TestIdentifier description) {
    if (description == FAKE_ROOT) {
      return myForkedTestPlan.getRoots()
        .stream()
        .flatMap(root -> myForkedTestPlan.getChildren(root).stream())
        .collect(Collectors.toList());
    }
    return new ArrayList<>(myForkedTestPlan.getChildren(description));
  }

  /**
   * {@link com.intellij.execution.junit.TestClass#getForkMode()}
   */
  @Override
  public String getStartDescription(TestIdentifier child) {
    if (!myForkedTestPlan.getParent(child).isPresent()) {
      //if fork mode is "repeat", then the only child is the corresponding class
      child = myForkedTestPlan.getChildren(child).iterator().next();
    }
    final TestIdentifier testIdentifier = child;
    final String className = JUnit5TestExecutionListener.getClassName(testIdentifier);
    final String methodSignature = JUnit5TestExecutionListener.getMethodSignature(testIdentifier);
    if (methodSignature != null) {
      return className + "," + methodSignature;
    }
    return className != null ? className : testIdentifier.getDisplayName();
  }

  @Override
  public String getTestClassName(TestIdentifier child) {
    return child.toString();
  }

  private static class MyCustomListenerWrapper implements TestExecutionListener {
    private final IDEAJUnitListener myJunitListener;

    MyCustomListenerWrapper(IDEAJUnitListener junitListener) {
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
