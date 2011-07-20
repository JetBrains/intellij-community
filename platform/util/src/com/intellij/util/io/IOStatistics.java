package com.intellij.util.io;

public class IOStatistics {
  static final boolean DEBUG = System.getProperty("io.access.debug") != null;
  public static final int MIN_IO_TIME_TO_REPORT = 100;

  public static void dump(String msg) {
    System.out.println(msg);
  }
}
