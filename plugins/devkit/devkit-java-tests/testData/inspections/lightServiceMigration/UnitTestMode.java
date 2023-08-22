package com.example.demo;

import com.intellij.openapi.application.ApplicationManager;

final class MyService {
  private final static boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
}