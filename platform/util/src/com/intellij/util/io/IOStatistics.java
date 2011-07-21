package com.intellij.util.io;

class IOStatistics {
  static final boolean DEBUG = System.getProperty("io.access.debug") != null;
  static final int MIN_IO_TIME_TO_REPORT = 100;

  static void dump(String msg) {
    System.out.println(msg);
  }
}
