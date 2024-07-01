// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.module

import com.intellij.openapi.module.Module
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

/**
 * Method-level [Module]s are destroyed after each test
 */
@TestApplication
class JUnit5MethodLevelModule {

  private val moduleFixture = projectFixture().moduleFixture()

  private var moduleFromPreviousTest: Module? = null

  @Test
  fun test() {
    val module1 = moduleFixture.get()
    val module2 = moduleFixture.get()
    Assertions.assertEquals(module1, module2, "Method level modules must be same")
    Assertions.assertFalse(module1.isDisposed)
    ensureNotModuleFromPrevTest(module1)
  }

  @Test
  fun testDifferentModule() {
    ensureNotModuleFromPrevTest(moduleFixture.get())
  }

  private fun ensureNotModuleFromPrevTest(module: Module) {
    Assertions.assertNotEquals(module, moduleFromPreviousTest)
    if (moduleFromPreviousTest == null) {
      moduleFromPreviousTest = module
    }
  }
}