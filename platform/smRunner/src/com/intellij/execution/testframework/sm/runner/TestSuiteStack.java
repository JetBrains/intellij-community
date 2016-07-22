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
package com.intellij.execution.testframework.sm.runner;

import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EmptyStackException;
import java.util.Stack;

/**
 * @author Roman Chernyatchik
 */
public class TestSuiteStack {
  private static final Logger LOG = Logger.getInstance(TestSuiteStack.class.getName());

  @NonNls private static final String EMPTY = "empty";

  private final Stack<SMTestProxy> myStack = new Stack<>();
  private final String myTestFrameworkName;

  /**
   * @deprecated use {@link #TestSuiteStack(String)} instead, to be removed in IDEA 16
   */
  public TestSuiteStack() {
    this("<unspecified>");
  }

  public TestSuiteStack(@NotNull String testFrameworkName) {
    myTestFrameworkName = testFrameworkName;
  }

  public void pushSuite(@NotNull final SMTestProxy suite) {
    myStack.push(suite);
  }

  /**
   * @return Top element of non stack or null for empty stack
   */
  @Nullable
  public SMTestProxy getCurrentSuite() {
    if (getStackSize() != 0) {
      return myStack.peek();
    }
    return null;
  }

  /**
   * Pop element form stack and checks consistency
   * @param suiteName Predictable name of top suite in stack. May be null if 
   */
  @Nullable
  public SMTestProxy popSuite(final String suiteName) throws EmptyStackException {
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
    for (int i = 0; i < stackSize; i++) {
      names[i] = myStack.get(i).getName();
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
