// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigMoveElementLeftRightHandlerTest : BasePlatformTestCase() {
  override fun getBasePath() =
    "/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/actions/moveLeftRight/"

  override fun isCommunity(): Boolean = true

  fun testMoveCentralValue() = doTest()
  fun testMoveFirstValue() = doTest()
  fun testMoveLastValue() = doTest()
  fun testMoveElementInPair() = doTest()
  fun testMoveFirstPattern() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    with(myFixture) {
      configureByFile("$testName/before/.editorconfig")
      performEditorAction(IdeActions.MOVE_ELEMENT_LEFT)
      checkResultByFile("$testName/afterLeft/.editorconfig")
    }
    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.file))
    with(myFixture) {
      configureByFile("$testName/before/.editorconfig")
      performEditorAction(IdeActions.MOVE_ELEMENT_RIGHT)
      checkResultByFile("$testName/afterRight/.editorconfig")
    }
  }
}
