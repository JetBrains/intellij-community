package com.intellij.openapi.application.fake;

public class ApplicationManager {
  public static Application getApplication() {
    return new Application() {
      @Override
      public void assertIsDispatchThread() {
        if ("TESTING_BACKGROUND_THREAD".equals(Thread.currentThread().getName())) {
          throw new RuntimeException("Access is allowed from event dispatch thread only.");
        }
      }

      @Override
      public void assertIsNonDispatchThread() {
        if (!"TESTING_BACKGROUND_THREAD".equals(Thread.currentThread().getName())) {
          throw new RuntimeException("Access from event dispatch thread is not allowed.");
        }
      }

      @Override
      public void assertReadAccessAllowed() {}

      @Override
      public void assertWriteAccessAllowed() {}
    };
  }
}
