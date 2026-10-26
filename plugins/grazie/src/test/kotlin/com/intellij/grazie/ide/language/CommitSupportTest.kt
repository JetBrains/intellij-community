@file:Suppress("NonAsciiCharacters")

package com.intellij.grazie.ide.language

import com.intellij.codeInspection.ex.InspectionProfileWrapper
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.vcs.commit.message.CommitMessageInspectionProfile
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class CommitSupportTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val grazie by grazieTestFixture(projectFixture)

  @Test
  fun `test commit message has highlighting with all quick fixes`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    configureCommit(grazie.fixture, """
            
            This is <caret><GRAMMAR_ERROR descr="EN_A_VS_AN">a</GRAMMAR_ERROR> error.
            
            This reverts commit 79a9b5d5451e9ba48c238cbe1191743d2f3e09d1.
            
            (cherry picked from commit 241965151ad2980e583e1629e91537e684a90598)
            
            das <TYPO descr="Typo: In word 'daert'">daert</TYPO> geschätzt fünf <STYLE_SUGGESTION descr="MANNSTUNDE">Mannstunden</STYLE_SUGGESTION>.
          """.trimIndent())

    grazie.fixture.checkHighlighting()

    grazie.fixture.findSingleIntention("Wrong article")
    grazie.fixture.findSingleIntention("an")
    grazie.fixture.findSingleIntention("Ignore 'a error'")
    grazie.fixture.findSingleIntention("Configure rule 'Use of 'a' vs. 'an'' in commit messages…")
  }

  @Test
  fun `test correct highlighting in commit messages`() {
    checkCommitMessage("JIRA-123: add apache commons text dependency")
    checkCommitMessage("JIRA-123: it started - june is here")
    checkCommitMessage("JIRA-123: installation of opera is required")
    checkCommitMessage("JIRA-123: fix bug when system was disabled if temperature is less than 20° celsius")
    checkCommitMessage("cleanup [Grazie]: june is here")
    checkCommitMessage("<TYPO descr=\"Typo: In word 'typopo'\">typopo</TYPO> [grazie]: june is here")
  }

  @Test
  fun `test mass apply is not available in commit messages`() {
    grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
    configureCommit(grazie.fixture, "This is <caret><GRAMMAR_ERROR descr=\"EN_A_VS_AN\">a</GRAMMAR_ERROR> error")
    grazie.fixture.checkHighlighting()
    assertNull(grazie.fixture.getAvailableIntention("Accept all writing suggestions…"))
  }

  @Test
  fun `test typos are highlighted under the real commit editor inspection profile`() {
    checkRealCommitHighlighting(grazie.fixture, "<TYPO descr=\"Typo: In word 'typopo'\">typopo</TYPO> [grazie]: june is here")
  }

  @Test
  fun `test spell checking is skipped when commit message checking is disabled`() {
    GrazieConfig.update { state ->
      state.copy(checkingContext = state.checkingContext.copy(isCheckInCommitMessagesEnabled = false))
    }
    service<GrazieCheckers>().awaitConfiguration()

    // No <TYPO> markup: with the "Commit messages" toggle off, CommitAnnotator must not report typos.
    checkRealCommitHighlighting(grazie.fixture, "typopo [grazie]: june is here")
  }

  private fun checkCommitMessage(text: String) {
    configureCommit(grazie.fixture, text)
    grazie.fixture.checkHighlighting()
  }
}

internal fun configureCommit(fixture: CodeInsightTestFixture, text: String) {
  fixture.configureByText("a.txt", text)
  val commitMessage = CommitMessage(fixture.project)
  Disposer.register(fixture.testRootDisposable, commitMessage)
  fixture.editor.document.putUserData(CommitMessage.DATA_KEY, commitMessage)
}

internal fun checkRealCommitHighlighting(fixture: CodeInsightTestFixture, text: String) {
  configureCommit(fixture, text)
  InspectionProfileWrapper.runWithCustomInspectionWrapper(fixture.file,
    { InspectionProfileWrapper(CommitMessageInspectionProfile.getInstance(fixture.project)) }) {
    fixture.checkHighlighting()
  }
}
