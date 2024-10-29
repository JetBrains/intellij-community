// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.testFramework.junit5.StressTestApplication
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@StressTestApplication
class JUnit5StressTest {

  @Test
  fun ensureStress() {
    assertNotNull(ApplicationManager.getApplication(), "Application should exist as stress mode depends on application")
    assertTrue(ApplicationManagerEx.isInStressTest(), "App should be in stress mode")
  }
}
