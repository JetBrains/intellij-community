// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

@TestDataPath("/inspections/canBeDumbAware")
class CanBeDumbAwareInspectionTest : LightDevKitInspectionFixTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/canBeDumbAware"

  override fun getFileExtension(): String = "java"

  override fun setUp() {
    super.setUp()

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

  fun testNotImplementingPossiblyDumbAware() {
    doTest()
  }

  fun testImplementingDumbAware() {
    doTest()
  }

  fun testNotImplementingDumbAware() {
    doTest()
  }

  fun testAbstractClassNotImplementingDumbAware() {
    doTest()
  }

  fun testNotImplementingDumbAwareByParent() {
    doTest()
  }

  fun testImplementingDumbAwareByParent() {
    doTest()
  }

  fun testOverridingIsDumbAware() {
    doTest()
  }

  fun testOverridingIsDumbAwareByParent() {
    doTest()
  }

  fun testOverridingIsDumbAwareByInterface() {
    doTest()
  }
}