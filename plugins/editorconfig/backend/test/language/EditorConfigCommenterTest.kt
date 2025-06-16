// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language

import com.intellij.codeInsight.generation.actions.CommentByLineCommentAction
import com.intellij.openapi.application.ex.PathManagerEx
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class EditorConfigCommenterTest : BasePlatformTestCase() {
  override fun getTestDataPath() =
    "${PathManagerEx.getCommunityHomePath()}/plugins/editorconfig/testData/org/editorconfig/language/commenter/"

  fun testComment() = doTest()
  fun testUncomment() = doTest()

  private fun doTest() {
    val name = getTestName(true)
    val source = "$name/.editorconfig"
    myFixture.configureByFile(source)
    CommentByLineCommentAction().actionPerformedImpl(project, myFixture.editor)
    val result = "$name/result.txt"
    myFixture.checkResultByFile(result)
  }
}
