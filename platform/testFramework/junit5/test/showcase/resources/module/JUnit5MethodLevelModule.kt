// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.module

import com.intellij.openapi.module.Module
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.resources.NoInject
import com.intellij.testFramework.junit5.resources.ProjectResource
import com.intellij.testFramework.junit5.resources.asExtension
import com.intellij.testFramework.junit5.resources.providers.module.ModuleProvider
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Method-level [Module]s are destroyed after each test
 */
@TestApplication
@ProjectResource
class JUnit5MethodLevelModule {

  @JvmField
  @RegisterExtension
  val ext = ModuleProvider().asExtension()

  @NoInject
  private var moduleFromPreviousTest: Module? = null

  @Test
  fun test(module1: Module, module2: Module) {
    Assertions.assertEquals(module1, module2, "Method level modules must be same")
    Assertions.assertFalse(module1.isDisposed)
    ensureNotModuleFromPrevTest(module1)
  }

  @Test
  fun testDifferentModule(module: Module) {
    ensureNotModuleFromPrevTest(module)
  }

  private fun ensureNotModuleFromPrevTest(module: Module) {
    Assertions.assertNotEquals(module, moduleFromPreviousTest)
    if (moduleFromPreviousTest == null) {
      moduleFromPreviousTest = module
    }
  }
}