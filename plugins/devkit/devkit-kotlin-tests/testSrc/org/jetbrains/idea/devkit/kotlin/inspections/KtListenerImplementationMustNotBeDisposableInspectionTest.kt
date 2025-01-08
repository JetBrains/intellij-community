// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.inspections.ListenerImplementationMustNotBeDisposableInspectionTestBase
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/listenerImplementationMustNotBeDisposable")
class KtListenerImplementationMustNotBeDisposableInspectionTest : ListenerImplementationMustNotBeDisposableInspectionTestBase() {

  override fun getBasePath() = DevkitKtTestsUtil.TESTDATA_PATH + "inspections/listenerImplementationMustNotBeDisposable"

  override fun getFileExtension(): String = "kt"

  fun testDisposableApplicationListener() {
    doTest()
  }

  fun testDisposableProjectListener() {
    doTest()
  }

  fun testDisposableListenerDeepInheritance() {
    doTest()
  }

  fun testDisposableUnregisteredListener() {
    doTest()
  }

  fun testNonDisposableApplicationListener() {
    doTest()
  }

  fun testNonDisposableProjectListener() {
    doTest()
  }

}