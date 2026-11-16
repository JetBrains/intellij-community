// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.lightDevKitInspectionFixture
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/canBeDumbAware")
@TestApplication
class CanBeDumbAwareInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val devKit by lightDevKitInspectionFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/canBeDumbAware")
  private val myFixture get() = devKit.fixture

  @BeforeEach
  fun setUp() {

    myFixture.enableInspections(CanBeDumbAwareInspection())

    myFixture.addClass("""
      package com.intellij.openapi.project;
      
      public interface DumbAware {}
    """.trimIndent())
    myFixture.addClass("""
      package com.intellij.openapi.project;
      
      public interface PossiblyDumbAware {
        default boolean isDumbAware() {
          //noinspection SSBasedInspection
          return this instanceof DumbAware;
        }
      }
    """.trimIndent())
  }

  @Test
  fun testNotImplementingPossiblyDumbAware() {
    devKit.doTest()
  }

  @Test
  fun testImplementingDumbAware() {
    devKit.doTest()
  }

  @Test
  fun testNotImplementingDumbAware() {
    devKit.doTest()
  }

  @Test
  fun testAbstractClassNotImplementingDumbAware() {
    devKit.doTest()
  }

  @Test
  fun testNotImplementingDumbAwareByParent() {
    devKit.doTest()
  }

  @Test
  fun testImplementingDumbAwareByParent() {
    devKit.doTest()
  }

  @Test
  fun testOverridingIsDumbAware() {
    devKit.doTest()
  }

  @Test
  fun testOverridingIsDumbAwareByParent() {
    devKit.doTest()
  }

  @Test
  fun testOverridingIsDumbAwareByInterface() {
    devKit.doTest()
  }
}