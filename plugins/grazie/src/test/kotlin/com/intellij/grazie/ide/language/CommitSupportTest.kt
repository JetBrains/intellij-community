package com.intellij.grazie.ide.language

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

class CommitSupportTest : GrazieTestBase() {
  fun `test commit message has highlighting with all quick fixes`() {
    enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    configureCommit(myFixture, """
            
            This is <caret><GRAMMAR_ERROR descr="EN_A_VS_AN">a</GRAMMAR_ERROR> error.
            
            This reverts commit 79a9b5d5451e9ba48c238cbe1191743d2f3e09d1.
            
            (cherry picked from commit 241965151ad2980e583e1629e91537e684a90598)
            
            das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <STYLE_SUGGESTION descr="MANNSTUNDE">Mannstunden</STYLE_SUGGESTION>.
          """.trimIndent())

    myFixture.checkHighlighting()

    myFixture.findSingleIntention("Wrong article")
    myFixture.findSingleIntention("an")
    myFixture.findSingleIntention("Ignore 'a error'")
    myFixture.findSingleIntention("Configure rule 'Use of 'a' vs. 'an''…")
  }
}

internal fun configureCommit(fixture: CodeInsightTestFixture, text: String) {
  fixture.configureByText("a.txt", text)
  val commitMessage = CommitMessage(fixture.project)
  Disposer.register(fixture.testRootDisposable, commitMessage)
  fixture.editor.document.putUserData(CommitMessage.DATA_KEY, commitMessage)
}
