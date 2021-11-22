package com.intellij.grazie.ide.language

import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
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
}