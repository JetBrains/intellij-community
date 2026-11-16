// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PluginPathManager
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.idea.devkit.inspections.applicationServiceAsStaticFinalFieldOrPropertyFixture
import org.jetbrains.kotlin.test.util.invalidateCaches
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@TestDataPath("/inspections/applicationServiceAsStaticFinalFieldOrProperty")
@TestApplication
class KtFirApplicationServiceAsStaticFinalFieldOrPropertyInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val appService by applicationServiceAsStaticFinalFieldOrPropertyFixture(
    projectFixture, "kt", PluginPathManager.getPluginHomePathRelative("devkit") +
                          "/devkit-kotlin-tests/testData/inspections/applicationServiceAsStaticFinalFieldOrProperty")
  private val myFixture get() = appService.fixture

  @AfterEach
  fun tearDown(): Unit = timeoutRunBlocking {
    withContext(Dispatchers.EDT) {
      myFixture.project.invalidateCaches()
    }
  }

  @Test
  fun testStaticProperties() {
    appService.doHighlightTest()
  }

  @Test
  fun testExplicitConstructorCall() {
    appService.doHighlightTest()
  }

  @Test
  fun testNonServices() {
    appService.doHighlightTest()
  }

  @Test
  fun testRegisteredServices() {
    appService.doHighlightTest()
  }

  @Test
  fun testLightServices() {
    appService.doHighlightTest()
  }

  @Test
  fun testBackingFields() {
    appService.doHighlightTest()
  }

  @Test
  fun testServiceInstances() {
    appService.doHighlightTest()
  }

  @Test
  fun testEagerInitializationDuringClassloading() {
    appService.doHighlightTest()
  }
}
