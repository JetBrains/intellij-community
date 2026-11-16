// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2

import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.k2.inspections.privateExtensionClassFixture
import org.jetbrains.idea.devkit.k2.inspections.privateExtensionClassProjectFixture
import org.junit.jupiter.api.Test

@TestApplication
class K2PrivateExtensionClassInspectionTest {
  companion object {
    private val projectFixture = privateExtensionClassProjectFixture()
  }

  private val devKit by privateExtensionClassFixture(projectFixture)

  @Test
  fun testExtensionClassWithPrivateModifier() {
    devKit.doTest()
  }

  @Test
  fun testActionClassWithPrivateModifier() {
    devKit.doTest()
  }

  @Test
  fun testServiceImplWithPrivateModifier() {
    devKit.doTest()
  }
}