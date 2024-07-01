// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.showcase.resources.module

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class JUnit5ClassLevelModule {

  companion object {

    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture()

  }


  @Test
  fun test() {
    val module1 = moduleFixture.get()
    val module2 = moduleFixture.get()
    Assertions.assertEquals(module1, module2, "Class level modules must be same")
    Assertions.assertFalse(module1.isDisposed)
  }
}