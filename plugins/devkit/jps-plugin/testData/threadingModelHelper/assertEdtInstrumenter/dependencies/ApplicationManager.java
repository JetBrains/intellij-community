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
        };
    }
}
