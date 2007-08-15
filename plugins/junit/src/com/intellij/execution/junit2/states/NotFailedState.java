package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.Printer;
import com.intellij.rt.execution.junit.states.PoolOfTestStates;

public final class NotFailedState extends TestState {
  private int myMagnitude;
  private final boolean myIsFinal;

  public NotFailedState(final int magnitude, final boolean aFinal) {
    myMagnitude = magnitude;
    myIsFinal = aFinal;
  }

  public int getMagnitude() {
    return myMagnitude;
  }

  public boolean isFinal() {
    return myIsFinal;
  }

  public void printOn(final Printer printer) {
  }

  public static NotFailedState createPassed() {
    return new NotFailedState(PoolOfTestStates.PASSED_INDEX, true);
  }

  public static TestState createTerminated() {
    return new NotFailedState(PoolOfTestStates.TERMINATED_INDEX, true);
  }
}
