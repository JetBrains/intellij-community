package com.intellij.execution.junit2;

import com.intellij.execution.junit2.states.Statistics;
import com.intellij.execution.junit2.states.TestState;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.Filter;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SuiteState extends TestState {
  private final TestProxy myTest;
  private final StateCache myCache = new StateCache();

  private static final CachedAcpect<List<TestProxy>> ALL_TESTS = new CachedAcpect<List<TestProxy>>(){
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
      return Filter.IN_PROGRESS.detectIn(state.myTest.getChildren()) != null ?
             Boolean.TRUE : Boolean.FALSE;
    }
  };

  private static final CachedAcpect<TestState.StateInterval> STATE_INTERVAL = new CachedAcpect<TestState.StateInterval>() {
    public TestState.StateInterval calculate(final SuiteState state) {
      return state.myTest.calculateInterval(state);
    }
  };

  private static final CachedAcpect<Integer> MAGNITUDE  = new CachedAcpect<Integer>() {
    public Integer calculate(final SuiteState state) {
      return calcIntMagnitude(state);
    }

    private int calcIntMagnitude(final SuiteState state) {
      final StateInterval interval = state.getInterval();
      final int maxState = interval.getMax().getMagnitude();
      final int minState = interval.getMin().getMagnitude();
      if (minState == maxState) return maxState;
      if (maxState <= PoolOfTestStates.RUNNING_INDEX &&
          minState < PoolOfTestStates.NOT_RUN_INDEX)
        return PoolOfTestStates.RUNNING_INDEX;
      return maxState;
    }
  };

  public SuiteState(final TestProxy test) {
    myTest = test;
    myTest.addListener(myCache);
  }

  public int getMagnitude() {
    return myCache.get(MAGNITUDE).intValue();
  }

  public void update() {
    myCache.invalidate();
  }

  public void printOn(final Printer printer) {
  }

  public TestState.StateInterval getInterval() {
    return myCache.get(STATE_INTERVAL);
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

  public void changeStateAfterAddingChaildTo(final TestProxy test, final TestProxy child) {
    if (child.getState().getMagnitude() <= getMagnitude()) {
      test.onStatisticsChanged(test);
      return;
    }
    test.onChanged(test);
  }

  private static abstract class CachedAcpect<T> {
    private static int ourNextInstanceIndex = 0;
    private final int myId;

    protected CachedAcpect() {
      synchronized(CachedAcpect.class) {
        myId = ourNextInstanceIndex;
        ourNextInstanceIndex++;
      }
    }

    public abstract T calculate(SuiteState state);

    public int getId() {
      return myId;
    }
  }

  public static class SuiteStateInterval extends TestState.StateInterval {
    private TestState myMin;
    private TestState myMax;

    public SuiteStateInterval(final TestState state, final TestState.StateInterval interval) {
      super(state);
      myMin = interval.getMin();
      myMax = interval.getMax();
    }

    public TestState getMin() {
      return myMin;
    }

    public TestState getMax() {
      return myMax;
    }

    public void updateFrom(final TestState.StateInterval interval) {
      final TestState otherMax = interval.getMax();
      if (myMax.getMagnitude() < otherMax.getMagnitude())
        myMax = otherMax;
      final TestState otherMin = interval.getMin();
      if (myMin.getMagnitude() > otherMin.getMagnitude())
        myMin = otherMin;
    }
  }

  private class StateCache implements TestProxyListener {
    private final Object[] myValues = new Object[5];

    public <T> T get(final CachedAcpect<T> aspect) {
      final int id = aspect.getId();
      T value = (T)myValues[id];
      if (value == null) {
        value = aspect.calculate(SuiteState.this);
        myValues[id] = value;
      }
      return value;
    }

    public void onChildAdded(final AbstractTestProxy testProxy, final AbstractTestProxy newChild) {
      if (newChild.getParent() == myTest)
        invalidate();
    }

    public void onChanged(final AbstractTestProxy test) {
      if (test == myTest)
        invalidate();
    }

    public void onStatisticsChanged(final AbstractTestProxy test) {
      if (test == myTest)
        invalidate();
    }

    public void invalidate() {
      Arrays.fill(myValues, null);
    }
  }
}
