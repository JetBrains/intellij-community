// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.DevkitJavaTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/listenerImplementationMustNotBeDisposable")
@TestApplication
class ListenerImplementationMustNotBeDisposableInspectionTest {
  companion object {
    private val projectFixture = pluginModuleProjectFixture()
  }

  private val listener by listenerImplementationMustNotBeDisposableFixture(projectFixture, "java", DevkitJavaTestsUtil.TESTDATA_PATH + "inspections/listenerImplementationMustNotBeDisposable")

  @Test
  fun testDisposableApplicationListener() {
    listener.doTest()
  }

  @Test
  fun testDisposableProjectListener() {
    listener.doTest()
  }

  @Test
  fun testDisposableListenerDeepInheritance() {
    listener.doTest()
  }

  @Test
  fun testDisposableUnregisteredListener() {
    listener.doTest()
  }

  @Test
  fun testDisposableLocalListener() {
    listener.doTest()
  }

  @Test
  fun testNonDisposableApplicationListener() {
    listener.doTest()
  }

  @Test
  fun testNonDisposableProjectListener() {
    listener.doTest()
  }

}