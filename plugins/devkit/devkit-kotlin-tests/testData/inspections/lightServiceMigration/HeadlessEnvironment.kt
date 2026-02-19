package com.example.demo

import com.intellij.openapi.application.ApplicationManager

class MyService {
  fun foo() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
    }
  }
}