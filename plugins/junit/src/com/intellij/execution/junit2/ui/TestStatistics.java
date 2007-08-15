package com.intellij.execution.junit2.ui;

import org.jetbrains.annotations.NonNls;

public interface TestStatistics {
  @NonNls String RUNNING_SUITE_PREFIX = "RUNNING: ";
  TestStatistics ABCENT = new NoStatistics("NO SELECTION");
  TestStatistics NOT_RUN = new NoStatistics("NOT RUN");
  TestStatistics RUNNING = new NoStatistics("RUNNING");

  String getTime();

  String getMemoryUsageDelta();

  String getBeforeMemory();

  String getAfterMemory();

  static class NoStatistics implements TestStatistics {
    private final String myMessage;

    public NoStatistics(@NonNls final String message) {
      myMessage = "<" + message + ">";
    }

    public String getTime() {
      return myMessage;
    }

    public String getMemoryUsageDelta() {
      return myMessage;
    }

    public String getBeforeMemory() {
      return myMessage;
    }

    public String getAfterMemory() {
      return myMessage;
    }

    public String toString() {
      return myMessage;
    }
  }
}
