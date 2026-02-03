// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase

import com.intellij.openapi.module.Module
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

@TestApplication
class JUnit5ModuleFixtureTest {

  private companion object {
    val sharedProject = projectFixture()
    val sharedModule = sharedProject.moduleFixture()

    var seenModule: Module? = null
  }

  private val localModuleInSharedProject = sharedProject.moduleFixture()
  private val localModuleInLocalProject = projectFixture().moduleFixture()

  @Test
  fun `fixture returns same instance`() {
    assertSame(sharedModule.get(), sharedModule.get())
    assertSame(localModuleInSharedProject.get(), localModuleInSharedProject.get())
    assertSame(localModuleInLocalProject.get(), localModuleInLocalProject.get())
  }

  @Test
  fun `modules are different`() {
    assertNotSame(sharedModule.get(), localModuleInSharedProject.get())
    assertNotSame(sharedModule.get(), localModuleInLocalProject.get())
    assertNotSame(localModuleInSharedProject.get(), localModuleInLocalProject.get())
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `shared module is kept between tests`(id: Int) {
    val module = sharedModule.get()
    assertFalse(module.isDisposed)
    if (id == 0) {
      seenModule = module
    }
    else {
      assertSame(seenModule, module)
      seenModule = null
    }
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `local module in shared project is recreated between tests`(id: Int) {
    moduleTest(localModuleInSharedProject.get(), id)
  }

  @ParameterizedTest
  @ValueSource(ints = [0, 1])
  fun `local module in local project is recreated between tests`(id: Int) {
    moduleTest(localModuleInLocalProject.get(), id)
  }

  private fun moduleTest(module: Module, id: Int) {
    assertFalse(module.isDisposed)
    if (id == 0) {
      seenModule = module
    }
    else {
      assertNotSame(seenModule, module)
      assertTrue(checkNotNull(seenModule).isDisposed)
      seenModule = null
    }
  }
}
