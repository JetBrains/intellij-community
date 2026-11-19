// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.k2.inspections

import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.idea.devkit.inspections.quickfix.lightProjectDescriptorEqualsHashCodeFixture
import org.jetbrains.idea.devkit.kotlin.DevkitKtTestsUtil
import org.junit.jupiter.api.Test

@TestDataPath("\$CONTENT_ROOT/testData/inspections/lightProjectDescriptorEqualsHashCode")
@TestApplication
class KtLightProjectDescriptorEqualsHashCodeInspectionTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val descriptorEquals by lightProjectDescriptorEqualsHashCodeFixture(projectFixture, "kt", DevkitKtTestsUtil.TESTDATA_PATH + "inspections/lightProjectDescriptorEqualsHashCode")

  @Test
  fun testFlagged() {
    descriptorEquals.doTest()
  }

  @Test
  fun testNotFlagged() {
    descriptorEquals.doTest()
  }
}
