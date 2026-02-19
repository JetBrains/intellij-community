package com.intellij.util.concurrency.fake;

public final class ThreadingAssertions {

  public static void assertEventDispatchThread() {
    if ("TESTING_BACKGROUND_THREAD".equals(Thread.currentThread().getName())) {
      throw new RuntimeException("Access is allowed from Event Dispatch Thread (EDT) only");
    }
  }

  public static void assertBackgroundThread() {
    if (!"TESTING_BACKGROUND_THREAD".equals(Thread.currentThread().getName())) {
      throw new RuntimeException("Access from Event Dispatch Thread (EDT) is not allowed");
    }
  }

  public static void softAssertReadAccess() {
  }

  public static void assertNoReadAccess() {
  }

  public static void assertWriteIntentReadAccess() {
  }

  public static void assertWriteAccess() {
  }
}
