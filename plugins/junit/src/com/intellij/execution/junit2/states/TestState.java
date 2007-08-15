package com.intellij.execution.junit2.states;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.Printable;
import com.intellij.execution.junit2.SuiteState;
import com.intellij.execution.junit2.TestProxy;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.pom.Navigatable;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

import java.util.Collections;
import java.util.List;

public abstract class TestState implements Printable {
  public static final TestState DEFAULT = new NotFailedState(PoolOfTestStates.NOT_RUN_INDEX, false);
  public static final TestState RUNNING_STATE = new NotFailedState(PoolOfTestStates.RUNNING_INDEX, false);

  private final StateInterval myInterval;

  public List<TestProxy> getAllTestsOf(final TestProxy test) {
    return Collections.singletonList(test);
  }

  public static class StateInterval {
    private final TestState myState;

    public StateInterval(final TestState state) {
      myState = state;
    }
    public TestState getMin() {
      return myState;
    }
    public TestState getMax() {
      return myState;
    }
  }

  public TestState() {
    myInterval = new StateInterval(this);
  }

  public abstract int getMagnitude();

  public StateInterval getInterval() {
    return myInterval;
  }

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

  public boolean isPassed() {
    return getMagnitude() == PoolOfTestStates.PASSED_INDEX;
  }

  public Navigatable getDescriptor(final Location<?> location) {
    if (location != null) return EditSourceUtil.getDescriptor(location.getPsiElement());
    return null;
  }

  public void changeStateAfterAddingChaildTo(final TestProxy test, final TestProxy child) {
    test.setState(new SuiteState(test));
  }
}
