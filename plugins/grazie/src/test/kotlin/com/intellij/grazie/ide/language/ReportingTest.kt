package com.intellij.grazie.ide.language

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix
import com.intellij.grazie.text.Rule
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.TextProblem
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ReportingTest : BasePlatformTestCase() {
  fun `test tooltip and description texts in inspection`() {
    val inspection = GrazieInspection()
    myFixture.enableInspections(inspection)
    myFixture.configureByText("a.txt", "I have an new apple here.")
    val info = assertOneElement(myFixture.doHighlighting().filter { it.inspectionToolId == inspection.id })
    val message = "Use a instead of 'an' if the following word doesn't start with a vowel sound, e.g. 'a sentence', 'a university'."
    assertEquals(info.description, message)
    assertTrue(info.toolTip, info.toolTip!!.matches(Regex(".*" + Regex.escape(message) + ".*Incorrect:.*Correct:.*")))
  }

  fun `test tooltip and description texts in commit annotator`() {
    configureCommit(myFixture, "I have an new apple here.")
    val info = assertOneElement(myFixture.doHighlighting().filter { it.description.contains("vowel") })
    val message = "Use a instead of 'an' if the following word doesn't start with a vowel sound, e.g. 'a sentence', 'a university'."
    assertEquals(info.description, message)
    assertTrue(info.toolTip, info.toolTip!!.matches(Regex(".*" + Regex.escape(message) + ".*Incorrect:.*Correct:.*")))
  }

  fun `test quick fix presentation`() {
    myFixture.configureByText("a.txt", "Some text")
    val text = TextExtractor.findTextAt(myFixture.file, 0, TextContent.TextDomain.ALL)!!
    val range = TextRange(0, 1)
    val rule = object : Rule("something.something", "something", "something") {
      override fun getDescription() = "something"
    }
    val problem = object : TextProblem(rule, text, range) {
      override fun getShortMessage() = "this problem"
      override fun getDescriptionTemplate(isOnTheFly: Boolean) = "something"
      override fun getReplacementRange() = highlightRange
      override fun getCorrections() = listOf(
        " ", // should be visible
        "another suggestion", // plain normal suggestion
        "" // should not be rendered as an empty string
      )
    }
    assertEquals(listOf("Fix 'this problem'", "' '", "another suggestion", "Remove"),
                 GrazieReplaceTypoQuickFix.getReplacementFixes(problem, emptyList()).map { it.name })
  }
}