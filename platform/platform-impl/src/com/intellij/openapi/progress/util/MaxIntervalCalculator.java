package com.intellij.openapi.progress.util;

public class MaxIntervalCalculator {
  private static long myLastTimeChecked;
  private static long myMaxInterval;
  private static int myTouchCount;
  private static long myStartTime;

  public static void touch(){
    /*
    long time = System.currentTimeMillis();
    long interval = time - myLastTimeChecked;
    if (interval > myMaxInterval){
      myMaxInterval = interval;
    }
    myLastTimeChecked = time;
    myTouchCount++;
    */
  }

  public static void startCalc(){
    /*
    myLastTimeChecked = myStartTime = System.currentTimeMillis();
    myMaxInterval = -1;
    myTouchCount = 0;
    */
  }

  public static void endCalc(){
    /*
    long time = System.currentTimeMillis();
    long totalTime = time - myStartTime;
    double avgInterval = totalTime / (double)myTouchCount;
    System.out.println("max interval = " + myMaxInterval);
    System.out.println("total time = " + totalTime);
    System.out.println("average interval = " + avgInterval);
    */
  }
}
