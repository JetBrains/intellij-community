// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil

@TestDataPath("\$CONTENT_ROOT/testData/inspections/listenerImplementationMustNotBeDisposable")
class ListenerImplementationMustNotBeDisposableInspectionTest : ListenerImplementationMustNotBeDisposableInspectionTestBase() {

  override fun getBasePath() = DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/listenerImplementationMustNotBeDisposable"

  override fun getFileExtension(): String = "java"

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