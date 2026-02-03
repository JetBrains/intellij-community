package com.intellij.grazie.pro

import com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CloudTextLevelSpellcheckingTest: BaseTestCase() {
  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(GrazieSpellCheckingInspection())
  }

  @NeedsCloud
  @Test
  fun `test cloud spellchecking`() {
    myFixture.configureByText("a.md", """
      Some additional context before the actual check to help language detector do its job.
      It’s in the top-left <TYPO descr="Typo: In word 'corter'"><caret>corter</TYPO> of the editor.
      Arnaldo della Torre wrote Storia dell'Academia Platonica di Firenze in 1902.
    """.trimIndent())
    myFixture.checkHighlighting()
    myFixture.launchAction(myFixture.findSingleIntention("corner"))
    myFixture.checkResult("""
      Some additional context before the actual check to help language detector do its job.
      It’s in the top-left corner of the editor.
      Arnaldo della Torre wrote Storia dell'Academia Platonica di Firenze in 1902.
    """.trimIndent())
  }

}