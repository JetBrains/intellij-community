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

import com.intellij.execution.junit2.TestProxy;
import com.intellij.execution.junit2.TestProxyListener;
import com.intellij.execution.junit2.states.CumulativeStatistics;
import com.intellij.execution.junit2.states.Statistics;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Printer;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SuiteState extends TestState {
  private final TestProxy myTest;
  private int myMaxMagnitude = PoolOfTestStates.NOT_RUN_INDEX;
  private boolean myHasRunning;
  private final StateCache myCache = new StateCache();

  public SuiteState(final TestProxy test) {
    myTest = test;
    myTest.addListener(new TestProxyListener() {
      public void onChildAdded(final AbstractTestProxy testProxy, final AbstractTestProxy newChild) {
        if (newChild.getParent() == myTest) {
          myCache.invalidate();
        }
      }

      public void onChanged(final AbstractTestProxy test) {
        if (test == myTest) myCache.invalidate();
      }

      public void onStatisticsChanged(final AbstractTestProxy test) {
        if (test == myTest) myCache.invalidate();
      }
    });
  }

  public int getMagnitude() {
    return myHasRunning ? PoolOfTestStates.RUNNING_INDEX : myMaxMagnitude;
  }

  public void update() {
    myCache.invalidate();
  }

  public void printOn(final Printer printer) {
  }


  public boolean isFinal() {
    return true;
  }

  public boolean isDefect() {
    return getMagnitude() >= PoolOfTestStates.FAILED_INDEX;
  }

  public boolean isInProgress() {
    return myCache.get(IS_IN_PROGRESS).booleanValue();
  }

  public Statistics getStatisticsFor(final TestProxy test) {
    return myCache.get(GET_STATISTICS);
  }

  public List<TestProxy> getAllTestsOf(final TestProxy test) {
    return myCache.get(ALL_TESTS);
  }

  public void changeStateAfterAddingChildTo(final TestProxy test, final TestProxy child) {
    if (child.getState().getMagnitude() <= getMagnitude()) {
      test.onStatisticsChanged();
      return;
    }
    test.onChanged(test);
  }

  public void setRunning(boolean running) {
    myHasRunning = running;
  }

  public void updateMagnitude(int magnitude) {
    if (myMaxMagnitude == PoolOfTestStates.NOT_RUN_INDEX) {
      myMaxMagnitude = magnitude;
    } else if (myMaxMagnitude < magnitude) {
      myMaxMagnitude = magnitude;
    }
  }

  private static final CachedAcpect<List<TestProxy>> ALL_TESTS = new CachedAcpect<List<TestProxy>>() {
    public List<TestProxy> calculate(final SuiteState state) {
      final ArrayList<TestProxy> allTests = new ArrayList<TestProxy>();
      state.myTest.collectAllTestsTo(allTests);
      return allTests;
    }
  };

  private static final CachedAcpect<Statistics> GET_STATISTICS = new CachedAcpect<Statistics>() {
    public Statistics calculate(final SuiteState state) {
      final CumulativeStatistics result = new CumulativeStatistics();
      for (final TestProxy testCase : state.myTest.getChildren()) {
        result.add(testCase.getStatistics());
      }
      return result;
    }
  };

  private static final CachedAcpect<Boolean> IS_IN_PROGRESS = new CachedAcpect<Boolean>() {
    public Boolean calculate(final SuiteState state) {
      for (TestProxy proxy : state.myTest.getChildren()) {
        if (proxy.isInProgress()) return Boolean.TRUE;
      }
      return Boolean.FALSE;
    }
  };


  private static abstract class CachedAcpect<T> {
    private static int ourNextInstanceIndex = 0;
    private final int myId;

    protected CachedAcpect() {
      synchronized (CachedAcpect.class) {
        myId = ourNextInstanceIndex;
        ourNextInstanceIndex++;
      }
    }

    public abstract T calculate(SuiteState state);

    public int getId() {
      return myId;
    }
  }


  private class StateCache {
    private final Object[] myValues = new Object[3];

    public <T> T get(final CachedAcpect<T> aspect) {
      final int id = aspect.getId();
      T value = (T)myValues[id];
      if (value == null) {
        value = aspect.calculate(SuiteState.this);
        myValues[id] = value;
      }
      return value;
    }

    public void invalidate() {
      Arrays.fill(myValues, null);
    }
  }
}
