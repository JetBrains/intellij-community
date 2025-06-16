// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.CodeInsightTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.editorconfig.language.codeinsight.refactoring.EditorConfigRenameHandler

class EditorConfigRefactoringTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/refactoring/"

  fun testRenameDeclarations() = doInplaceTest("my_rule")
  fun testRenameReferenceAndDeclarations() = doInplaceTest("my_symbols")
  fun testRenameDeclarationAndReferences() = doInplaceTest("my_style")

  fun testCrossFileRename() {
    val testName = getTestName(true)
    val before = "$testName/before/.editorconfig"
    val beforeSubfolder = "$testName/before/subfolder/.editorconfig"
    val after = "$testName/after/.editorconfig"
    val afterSubfolder = "$testName/after/subfolder/.editorconfig"
    with(myFixture) {
      configureByFiles(before, beforeSubfolder)
      renameElementAtCaret("my_new_style")
      checkResultByFile(before, after, false)
      checkResultByFile(beforeSubfolder, afterSubfolder, false)
    }
  }

  private fun doInplaceTest(newName: String) {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/before/.editorconfig")
    CodeInsightTestUtil.doInlineRename(EditorConfigRenameHandler(), newName, myFixture)
    myFixture.checkResultByFile("$testName/after/.editorconfig")
  }
}
