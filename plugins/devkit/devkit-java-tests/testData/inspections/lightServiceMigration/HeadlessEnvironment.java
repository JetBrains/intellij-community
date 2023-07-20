package com.example.demo;

import com.intellij.openapi.application.ApplicationManager;

final class MyService {
  public void foo() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
    }
  }
}