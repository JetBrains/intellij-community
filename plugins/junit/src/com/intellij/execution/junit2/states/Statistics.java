package com.intellij.execution.junit2.states;

import com.intellij.execution.junit2.segments.ObjectReader;

public class Statistics {
  public int myTime = 0;
  protected int myBeforeMemory = 0;
  protected int myAfterMemory = 0;

  public Statistics(final ObjectReader reader) {
    myTime = reader.readInt();
    myBeforeMemory = reader.readInt();
    myAfterMemory = reader.readInt();
  }

  public Statistics() {
  }

  public int getTime() {
    return myTime;
  }

  public int getBeforeMemory() {
    return myBeforeMemory;
  }

  public int getAfterMemory() {
    return myAfterMemory;
  }

  public int getMemoryUsage() {
    return myAfterMemory - myBeforeMemory;
  }
}
