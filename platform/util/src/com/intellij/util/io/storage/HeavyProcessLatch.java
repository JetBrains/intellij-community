/*
 * @author max
 */
package com.intellij.util.io.storage;

public class HeavyProcessLatch {
  public static final HeavyProcessLatch INSTANCE = new HeavyProcessLatch();
  private int myHeavyProcessCounter = 0;
  
  public synchronized void processStarted() {
    myHeavyProcessCounter++;
  }

  public synchronized void processFinished() {
    myHeavyProcessCounter--;
  }

  public synchronized boolean isRunning() {
    return myHeavyProcessCounter != 0;
  }
}