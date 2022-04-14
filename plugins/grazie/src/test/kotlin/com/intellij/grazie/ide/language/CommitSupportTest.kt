package com.intellij.grazie.ide.language

import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

class CommitSupportTest : BasePlatformTestCase() {
  fun `test commit message has highlighting with all quick fixes`() {
    configureCommit(myFixture, """
            
            This is <caret><warning>a</warning> error.
            
            This reverts commit abcdef00.
            
            (cherry picked from commit cafebabe)
          """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.findSingleIntention("Wrong article")
    myFixture.findSingleIntention("an")
    myFixture.findSingleIntention("Ignore 'a error'")
    myFixture.findSingleIntention("Configure rule 'Use of 'a' vs. 'an''...")
  }
}

internal fun configureCommit(fixture: CodeInsightTestFixture, text: String) {
  fixture.configureByText("a.txt", text)
  val commitMessage = CommitMessage(fixture.project)
  Disposer.register(fixture.testRootDisposable, commitMessage)
  fixture.editor.document.putUserData(CommitMessage.DATA_KEY, commitMessage)
}
