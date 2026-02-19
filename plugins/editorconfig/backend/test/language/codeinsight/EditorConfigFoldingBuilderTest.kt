// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigFoldingBuilderTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/folding/"

  fun testSection() = doTest()
  fun testSimpleComment() = doTest()
  fun testBlankLineBetweenComments() = doTest()
  fun testCommentInsideSection() = doTest()
  fun testCommentBetweenHeaderAndOption() = doTest()
  fun testMultipleSections() = doTest()
  fun testTrailingComment() = doTest()
  fun testLongComment() = doTest()

  private fun doTest() =
    myFixture.testFolding("${testDataPath}${getTestName(true)}/.editorconfig")
}
