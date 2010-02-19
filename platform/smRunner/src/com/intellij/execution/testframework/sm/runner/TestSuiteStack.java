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

import com.intellij.openapi.diagnostic.Logger;
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

  private final Stack<SMTestProxy> myStack = new Stack<SMTestProxy>();

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
   * @param suiteName Predictable name of top suite in stack
   */
  @NotNull
  public SMTestProxy popSuite(final String suiteName) throws EmptyStackException {
    if (myStack.isEmpty()) {
      LOG.error(
        "Pop error: Test runner tried to close test suite which has been already closed or wasn't started at all. Unexpected suite name [" +
        suiteName + "]");
      return null;
    }
    final SMTestProxy currentSuite = myStack.pop();

    if (!suiteName.equals(currentSuite.getName())) {
      LOG.error("Pop error: Unexpected closing suite. Expected [" + suiteName + "] but [" + currentSuite.getName() +
                "] was found. Rest of stack: " + getSuitePathPresentation());
      return null;
    }

    return currentSuite;
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

    final StringBuilder builder = new StringBuilder();
    final String lastName = names[names.length - 1];
    for (String name : names) {
      builder.append('[').append(name).append(']');
      //Here we can use != instead of !equals()
      //noinspection StringEquality
      if (name != lastName) {
        builder.append("->");
      }
    }
    return builder.toString();
  }

  public void clear() {
    myStack.clear();
  }
}
