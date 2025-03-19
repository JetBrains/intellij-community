// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EmptyStackException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author Roman Chernyatchik
 */
public class TestSuiteStack {
  private static final Logger LOG = Logger.getInstance(TestSuiteStack.class.getName());

  private static final @NonNls String EMPTY = "empty";

  private final ConcurrentLinkedDeque<SMTestProxy> myStack = new ConcurrentLinkedDeque<>();
  private final String myTestFrameworkName;

  public TestSuiteStack(@NotNull String testFrameworkName) {
    myTestFrameworkName = testFrameworkName;
  }

  public void pushSuite(final @NotNull SMTestProxy suite) {
    myStack.push(suite);
  }

  /**
   * @return Top element of non stack or null for empty stack
   */
  public @Nullable SMTestProxy getCurrentSuite() {
    return myStack.peek();
  }

  /**
   * Pop element form stack and checks consistency
   * @param suiteName Predictable name of top suite in stack. May be null if 
   */
  public @Nullable SMTestProxy popSuite(final String suiteName) throws EmptyStackException {
    if (myStack.isEmpty()) {
      if (SMTestRunnerConnectionUtil.isInDebugMode()) {
        LOG.error(
          "Pop error: Tests/suites stack is empty. Test runner tried to close test suite " +
          "which has been already closed or wasn't started at all. Unexpected suite name [" +
          suiteName + "]");
      }
      return null;
    }
    final SMTestProxy topSuite = myStack.peek();
    if (suiteName == null) {
      String msg = "Pop error: undefined suite name. Rest of stack: " + getSuitePathPresentation();
      GeneralTestEventsProcessor.logProblem(LOG, msg, true, myTestFrameworkName);
      return null;
    }

    if (!suiteName.equals(topSuite.getName())) {
      if (SMTestRunnerConnectionUtil.isInDebugMode()) {
        LOG.error("Pop error: Unexpected closing suite. Expected [" + suiteName + "] but [" + topSuite.getName() +
                  "] was found. Rest of stack: " + getSuitePathPresentation());
      } else {
        // let's try to switch to consistent state
        // 1. If expected suite name is somewhere in stack - let's find it and drop rest head of the stack
        SMTestProxy expectedProxy = null;
        for (SMTestProxy candidateProxy : myStack) {
          if (suiteName.equals(candidateProxy.getName())) {
            expectedProxy = candidateProxy;
            break;
          }
        }
        if (expectedProxy != null) {
          // drop all tests above it
          SMTestProxy proxy = topSuite;
          while (proxy != expectedProxy) {
            proxy = myStack.pop();
          }

          return expectedProxy;
        } else {
          // 2. if expected suite wasn't found let's skip it and return null
          return null;
        }
      }
      return null;
    } else {
      myStack.pop();
    }

    return topSuite;
  }

  public final boolean isEmpty() {
    return getStackSize() == 0;
  }
  
  protected int getStackSize() {
    return myStack.size();
  }

  protected String[] getSuitePath() {
    final int stackSize = getStackSize();
    final String[] names = new String[stackSize];
    int i = 0;
    for (Iterator<SMTestProxy> it = myStack.descendingIterator(); it.hasNext();) {
      names[i++] = it.next().getName();
    }
    return names;
  }

  protected String getSuitePathPresentation() {
    final String[] names = getSuitePath();
    if (names.length == 0) {
      return EMPTY;
    }

    return StringUtil.join(names, s -> "[" + s + "]", "->");
  }

  public void clear() {
    myStack.clear();
  }
}
