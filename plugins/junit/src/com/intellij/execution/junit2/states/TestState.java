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

package com.intellij.execution.junit2.states;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.testframework.Printable;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.pom.Navigatable;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class TestState implements Printable {
  public static final TestState DEFAULT = new NotFailedState(PoolOfTestStates.NOT_RUN_INDEX, false);
  public static final TestState RUNNING_STATE = new NotFailedState(PoolOfTestStates.RUNNING_INDEX, false);

  protected int myMagnitude;

  public List<TestProxy> getAllTestsOf(final TestProxy test) {
    final ArrayList<TestProxy> allTests = new ArrayList<TestProxy>();
    test.collectAllTestsTo(allTests);
    return allTests;
  }

  public abstract int getMagnitude();

  public abstract boolean isFinal();

  public boolean isDefect() {
    return false;
  }

  public boolean isInProgress() {
    return !isFinal();
  }

  public Statistics getStatisticsFor(final TestProxy test) {
    return test.getStatisticsImpl();
  }

  public void update() {
  }

  public void merge(@NotNull TestState state){}

  public boolean isPassed() {
    return getMagnitude() == PoolOfTestStates.PASSED_INDEX;
  }

  public Navigatable getDescriptor(final Location<?> location) {
    if (location != null) return EditSourceUtil.getDescriptor(location.getPsiElement());
    return null;
  }

  public void changeStateAfterAddingChildTo(final TestProxy test, final TestProxy child) {
    test.setState(new SuiteState(test));
  }
}
