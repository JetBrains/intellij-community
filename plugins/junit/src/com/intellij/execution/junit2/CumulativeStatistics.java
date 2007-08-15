package com.intellij.execution.junit2;

import com.intellij.execution.junit2.states.Statistics;

public class CumulativeStatistics extends Statistics {
  private int myMemoryUsage = 0;
  private boolean myIsEmpty = true;
  public CumulativeStatistics() {
  }

  public void add(final Statistics statistics) {
    myTime += statistics.getTime();
    myMemoryUsage += statistics.getMemoryUsage();
    if (myIsEmpty)
      myBeforeMemory = statistics.getBeforeMemory();
    myAfterMemory = statistics.getAfterMemory();
    myIsEmpty = false;
  }

  public int getMemoryUsage() {
    return myMemoryUsage;
  }
}
