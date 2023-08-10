// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/incorrectPceHandling")
class IncorrectProcessCanceledExceptionHandlingInspectionTest : IncorrectProcessCanceledExceptionHandlingInspectionTestBase() {

  override fun setUp() {
    super.setUp()
    myFixture.addClass("""
        package com.example;
        import com.intellij.openapi.progress.ProcessCanceledException;
        public class SubclassOfProcessCanceledException extends ProcessCanceledException {}
        """.trimIndent())
  }

  fun testIncorrectPceHandlingTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenMultipleCatchClausesTests() {
    doTest()
  }

  fun testIncorrectPceHandlingWhenPceCaughtImplicitlyTests() {
    doTest()
  }

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/incorrectPceHandling"

  override fun getFileExtension() = "java"

}
