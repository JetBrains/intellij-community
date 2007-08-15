package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.segments.ObjectReader;

abstract class ReadableState extends TestState {
  private int myMagnitude;

  abstract void initializeFrom(ObjectReader reader);

  public int getMagnitude() {
    return myMagnitude;
  }

  public void setMagitude(final int magnitude) {
    myMagnitude = magnitude;
  }

  public boolean isFinal() {
    return true;
  }
}
