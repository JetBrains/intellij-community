package com.intellij.execution.junit2.ui;

import com.intellij.execution.junit2.CumulativeStatistics;
import com.intellij.execution.junit2.states.Statistics;

class ActualStatistics implements TestStatistics {
  private final CumulativeStatistics myStatistics = new CumulativeStatistics();
  private String myPrefix = "";

  public ActualStatistics(final Statistics statistics) {
    myStatistics.add(statistics);
  }

  public void setRunning() {
    myPrefix = TestStatistics.RUNNING_SUITE_PREFIX;
  }

  public String getTime() {
    return myPrefix + Formatters.printTime(myStatistics.getTime());
  }

  public String getMemoryUsageDelta() {
    return showMemory(myStatistics.getMemoryUsage());
  }

  public String getBeforeMemory() {
    return showMemory(myStatistics.getBeforeMemory());
  }

  public String getAfterMemory() {
    return showMemory(myStatistics.getAfterMemory());
  }

  private String showMemory(final int memoryUsage) {
    return myPrefix + Formatters.printFullKBMemory(memoryUsage);
  }
}
