package com.intellij.openapi.application.fake;

public class ApplicationManager {
  public static Application getApplication() {
    return new Application() {
      @Override
      public void assertIsDispatchThread() {
        if ("TESTING_BACKGROUND_THREAD".equals(Thread.currentThread().getName())) {
          throw new RuntimeException("Access is allowed from Event Dispatch Thread (EDT) only");
        }
      }

      @Override
      public void assertIsNonDispatchThread() {
        if (!"TESTING_BACKGROUND_THREAD".equals(Thread.currentThread().getName())) {
          throw new RuntimeException("Access from Event Dispatch Thread (EDT) is not allowed");
        }
      }

      @Override
      public void assertReadAccessAllowed() {}

      @Override
      public void assertWriteAccessAllowed() {}

      @Override
      public void assertReadAccessNotAllowed() {}
    };
  }
}
