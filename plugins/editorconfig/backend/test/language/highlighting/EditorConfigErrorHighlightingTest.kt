// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.highlighting

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigErrorHighlightingTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/highlighting/error/"

  fun testDanglingDot() = doTest()
  fun testInnerDots() = doTest()
  fun testSuspiciousLineBreak() = doTest()
  fun testValueWithSpaces() = doTest()

  private fun doTest() {
    myFixture.testHighlighting(true, false, true, "${getTestName(true)}/.editorconfig")
  }
}
