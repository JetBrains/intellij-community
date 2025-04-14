// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.codeinsight.actions

import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EnterInEditorConfigFileHandlerTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/codeinsight/actions/enter/"

  fun testEnterInsideOfWordInComment() = doTest()
  fun testEnterBeforeSpaceInComment() = doTest()
  fun testEnterAfterComment() = doTest()
  fun testEnterBeforeComment() = doTest()

  private fun doTest() {
    val testName = getTestName(true)
    myFixture.configureByFile("$testName/.editorconfig")
    myFixture.type("\n")
    myFixture.checkResultByFile("$testName/result.txt")
  }
}
