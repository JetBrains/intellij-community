package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.junit.segments.Packet;

public class TestMeter {
  private final long myStartTime;

  private final long myInitialUsedMemory;
  private long myFinalMemory;
  private long myDuration;

  private boolean myIsStopped = false;


  public TestMeter() {
    myStartTime = System.currentTimeMillis();
    myInitialUsedMemory = usedMemory();
  }

  private long usedMemory() {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
  }

  public void stop() {
    if (!myIsStopped) {
      myDuration = System.currentTimeMillis() - myStartTime;
      myFinalMemory = usedMemory();
      myIsStopped = true;
    }
  }

  public void writeTo(Packet packet) {
    packet.addLong(myDuration).
        addLong(myInitialUsedMemory).
        addLong(myFinalMemory);
  }
}
