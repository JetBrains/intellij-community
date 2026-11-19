// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class InstanceIElementTypeFieldInspectionKotlinTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val devKit by instanceIElementTypeFieldFixture(projectFixture, "kt")

  @Test
  fun testInstancePropertyWithIElementType() {
    devKit.doTest()
  }

  @Test
  fun testCompanionObjectNoWarning() {
    devKit.doTest()
  }

  @Test
  fun testObjectDeclarationNoWarning() {
    devKit.doTest()
  }

  @Test
  fun testQualifiedNameWithLanguage() {
    devKit.doTest()
  }

  @Test
  fun testEnumFieldsNoWarning() {
    devKit.doTest()
  }
}
