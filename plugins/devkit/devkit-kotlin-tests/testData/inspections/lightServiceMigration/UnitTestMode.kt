package com.example.demo

import com.intellij.openapi.application.ApplicationManager

class MyService(val isUnitTestMode: Boolean = ApplicationManager.getApplication().isUnitTestMode)