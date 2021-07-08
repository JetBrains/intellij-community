package com.intellij.grazie.ide.language

import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CommitSupportTest : BasePlatformTestCase() {
  fun `test commit message has highlighting with all quick fixes`() {
    myFixture.configureByText("a.txt", """
      
      This is <caret><warning descr="EN_A_VS_AN">a</warning> error.
    """.trimIndent())

    val commitMessage = CommitMessage(project)
    disposeOnTearDown(commitMessage)
    myFixture.editor.document.putUserData(CommitMessage.DATA_KEY, commitMessage)

    myFixture.checkHighlighting()

    myFixture.findSingleIntention("Wrong article")
    myFixture.findSingleIntention("an")
    myFixture.findSingleIntention("Add exception 'a error'")
    myFixture.findSingleIntention("Rule settings 'Use of 'a' vs. 'an''...")
  }
}