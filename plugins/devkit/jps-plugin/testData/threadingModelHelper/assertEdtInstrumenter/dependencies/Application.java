package com.intellij.openapi.application.fake;

public interface Application {
    void assertIsDispatchThread();
    void assertIsNonDispatchThread();
    void assertReadAccessAllowed();
    void assertWriteAccessAllowed();
}
