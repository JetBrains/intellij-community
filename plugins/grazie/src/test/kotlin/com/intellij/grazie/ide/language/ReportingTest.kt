package com.intellij.grazie.ide.language

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption
import com.intellij.codeInsight.intention.CustomizableIntentionAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionBean
import com.intellij.codeInsight.intention.impl.config.IntentionManagerImpl
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.inspection.grammar.quickfix.GrazieReplaceTypoQuickFix
import com.intellij.grazie.text.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.text.StringOperation
import java.util.*

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

  fun `test unpaired parenthesis tooltip`() {
    val inspection = GrazieInspection()
    myFixture.enableInspections(inspection)
    myFixture.configureByText("a.txt", "I have a (new apple here.")
    val info = assertOneElement(myFixture.doHighlighting().filter { it.inspectionToolId == inspection.id })
    assertTrue(info.toolTip, info.toolTip!!.matches(Regex(".*Incorrect:.*" +
                                                          "I'm over here, she said" +
                                                          ".*Correct:.*" +
                                                          "I'm over here,.*&quot;.*she said" +
                                                          ".*")))
  }

  fun `test tooltip and description texts in commit annotator`() {
    configureCommit(myFixture, "I have an new apple here.")
    val info = assertOneElement(myFixture.doHighlighting().filter { it.description.contains("vowel") })
    val message = "Use a instead of 'an' if the following word doesn't start with a vowel sound, e.g. 'a sentence', 'a university'."
    assertEquals(info.description, message)
    assertTrue(info.toolTip, info.toolTip!!.matches(Regex(".*" + Regex.escape(message) + ".*Incorrect:.*Correct:.*")))
  }

  fun `test changed range highlighting`() {
    myFixture.enableInspections(GrazieInspection::class.java)
    myFixture.configureByText("a.txt", "Hello there! You <GRAMMAR_ERROR>are <caret>best</GRAMMAR_ERROR> person!")
    myFixture.checkHighlighting()

    val editor = myFixture.editor

    val intention = myFixture.findSingleIntention("are the best")
    val highlighted = assertOneElement((intention as CustomizableIntentionAction).getRangesToHighlight(editor, myFixture.file))

    val text = editor.document.text
    assertEquals(TextRange(text.indexOf("are"), text.indexOf(" person")), highlighted.rangeInFile)
  }

  fun `test quick fix presentation`() {
    myFixture.configureByText("a.txt", "Some text")
    val problem = mockProblem(TextExtractor.findTextAt(myFixture.file, 0, TextContent.TextDomain.ALL)!!, TextRange(0, 1), listOf(
      " ", // should be visible
      "another suggestion", // plain normal suggestion
      "" // should not be rendered as an empty string
    ), emptyList())
    assertEquals(listOf("Fix 'this problem'", "' '", "another suggestion", "Remove"),
                 GrazieReplaceTypoQuickFix.getReplacementFixes(problem, emptyList()).map { it.name })
  }

  fun `test batch quick fix presence`() {
    myFixture.configureByText("a.txt", "Some text")
    val range = TextRange(0, 1)
    val problem = mockProblemWithSuggestions(TextExtractor.findTextAt(myFixture.file, 0, TextContent.TextDomain.ALL)!!, range, listOf(
      object: TextProblem.Suggestion {
        override fun getChanges(): List<StringOperation> = listOf(StringOperation.replace(range, "x"))
        override fun getPresentableText() = "x"
        override fun getBatchId() = "batchX"
      },
      object: TextProblem.Suggestion {
        override fun getChanges(): List<StringOperation> = listOf(StringOperation.replace(range, "y"))
        override fun getPresentableText() = "y"
      }
    ), emptyList())
    val fixes = GrazieReplaceTypoQuickFix.getReplacementFixes(problem, emptyList())
    assertEquals(listOf("Fix 'this problem'", "x", "y"), fixes.map { it.name })

    assertTrue((fixes[1] as CustomizableIntentionAction).isShowSubmenu)
    assertFalse((fixes[2] as CustomizableIntentionAction).isShowSubmenu)

    val applyBatch = assertOneElement((fixes[1] as IntentionActionWithFixAllOption).options)
    assertEquals(GrazieBundle.message("grazie.grammar.quickfix.apply.batch.text"), applyBatch.text)

    assertEmpty((fixes[2] as IntentionActionWithFixAllOption).options)
  }

  fun `test quick fix sorting`() {
    myFixture.enableInspections(GrazieInspection())

    val testChecker = object: TextChecker() {
      override fun getRules(locale: Locale) = emptyList<Rule>()
      override fun check(extracted: TextContent): Collection<TextProblem> {
        val fixes = listOf(
          object : LocalQuickFix {
            override fun getFamilyName() = "z fix"
            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {}
          },
          object : LocalQuickFix {
            override fun getFamilyName() = "a fix"
            override fun applyFix(project: Project, descriptor: ProblemDescriptor) {}
          },
          MockIntentionAndFix()
        )
        return listOf(mockProblem(extracted, TextRange(0, 1), listOf("first", "second", "last"), fixes))
      }
    }
    ExtensionTestUtil.maskExtensions(ExtensionPointName("com.intellij.grazie.textChecker"), listOf(testChecker), testRootDisposable)

    ExtensionTestUtil.maskExtensions(
      IntentionManagerImpl.EP_INTENTION_ACTIONS, listOf(intentionBean<MockIntention>(), intentionBean<MockIntentionAndFix>()),
      testRootDisposable
    )

    myFixture.configureByText("a.txt", "Some text")
    myFixture.doHighlighting()
    assertOrderedEquals(
      myFixture.availableIntentions.map { it.text }.filter { !isAuxiliaryIntention(it) },
      "this problem",
      "first", "second", "last", // first suggestions go in the specified order
      "z fix", "a fix", // then custom fixes, in the specified order
      "mock intention and fix", // if a custom fix overrides an intention, it's raised in the list
      "Ignore 'S' in this sentence", // then the built-in general context action
      "Configure rule 'something'...",
      "mock intention", // normal intentions are at the bottom
    )
  }

  inline fun <reified T> intentionBean() = IntentionActionBean().also {
    it.className = T::class.java.name
    it.pluginDescriptor = DefaultPluginDescriptor("grazie test")
  }

  private open class MockIntention: IntentionAction {
    override fun getText() = "mock intention"
    override fun getFamilyName() = text
    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?) = true
    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {}
    override fun startInWriteAction() = false
  }

  private class MockIntentionAndFix: MockIntention(), LocalQuickFix {
    override fun getText() = "mock intention and fix"
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {}
  }

  private fun isAuxiliaryIntention(text: String) =
    text.contains("Edit inspection")
    || text.contains("Run inspection")
    || text.contains("Disable inspection")
    || text.contains("Disable highlighting, keep fix")
    || text.contains("Fix all")
    || text == CodeInsightBundle.message("assign.intention.shortcut")
    || text == CodeInsightBundle.message("edit.intention.shortcut")
    || text == CodeInsightBundle.message("remove.intention.shortcut")

  private fun mockProblem(text: TextContent, range: TextRange, corrections: List<String>, customFixes: List<LocalQuickFix>): TextProblem {
    return mockProblemWithSuggestions(text, range, corrections.map { TextProblem.Suggestion.replace(range, it) }, customFixes)
  }

  private fun mockProblemWithSuggestions(text: TextContent,
                                         range: TextRange,
                                         suggestions: List<TextProblem.Suggestion>,
                                         customFixes: List<LocalQuickFix>): TextProblem {
    val rule = object : Rule("something.something", "something", "something") {
      override fun getDescription() = "something"
    }
    return object : TextProblem(rule, text, range) {
      override fun getShortMessage() = "this problem"
      override fun getDescriptionTemplate(isOnTheFly: Boolean) = "something"
      override fun getSuggestions() = suggestions
      override fun getCustomFixes() = customFixes
    }
  }
}
