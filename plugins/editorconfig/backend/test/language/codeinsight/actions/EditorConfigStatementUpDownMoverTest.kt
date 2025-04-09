// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigStatementUpDownMoverTest : BasePlatformTestCase() {
  override fun getBasePath() =
    "/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/actions/moveUpDown/"

  override fun isCommunity(): Boolean = true

  fun testMoveFirstOption() = doTest()
  fun testMoveCentralOption() = doTest()
  fun testMoveLastOption() = doTest()

  fun testMoveOptionBetweenSections() = doTest()

  fun testMoveFirstSection() = doTest()
  fun testMoveCentralSection() = doTest()
  fun testMoveLastSection() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    with(myFixture) {
      configureByFile("$testName/before/.editorconfig")
      performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_UP_ACTION)
      checkResultByFile("$testName/afterUp/.editorconfig")
    }
    FileDocumentManager.getInstance().reloadFromDisk(myFixture.getDocument(myFixture.file))
    with(myFixture) {
      configureByFile("$testName/before/.editorconfig")
      performEditorAction(IdeActions.ACTION_MOVE_STATEMENT_DOWN_ACTION)
      checkResultByFile("$testName/afterDown/.editorconfig")
    }
  }
}
