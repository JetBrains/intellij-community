package com.intellij.execution.junit2.info;

import com.intellij.execution.junit2.segments.PacketReader;

abstract class TestInfoImpl implements TestInfo, PacketReader {
  private int myTestCount;

  public boolean shouldRun() {
    return false;
  }

  public int getTestsCount() {
    return myTestCount;
  }

  public void setTestCount(final int testCount) {
    myTestCount = testCount;
  }

  public void onFinished() {
  }
}
