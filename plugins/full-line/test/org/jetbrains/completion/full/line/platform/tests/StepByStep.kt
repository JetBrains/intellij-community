package org.jetbrains.completion.full.line.platform.tests

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.completion.full.line.FullLineProposal
import org.jetbrains.completion.full.line.RawFullLineProposal
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.services.TestFullLineCompletionProvider
import org.junit.jupiter.api.Assertions.assertEquals

class StepByStep(val myFixture: CodeInsightTestFixture) {
  @DslMarker
  annotation class CompletionBehavior

  @DslMarker
  annotation class SuggestionPick

  @DslMarker
  annotation class SuggestionList

  private val currentLine = StringBuilder()
  private val completionStartOffset = myFixture.caretOffset

  lateinit var pickingSuggestion: String

  @CompletionBehavior
  fun tab(expected: String, nextSuggestion: String? = null) = selectAndCheck(expected, '\t', nextSuggestion)

  @CompletionBehavior
  fun enter(expected: String, newSuggestion: String? = null) = selectAndCheck(expected, '\n', newSuggestion)

  @SuggestionPick
  fun withSuggestion(suggestion: String, block: StepByStep.() -> Unit) {
    patchProvider(suggestion)

    myFixture.complete(CompletionType.BASIC)
    pickingSuggestion = suggestion
    block()
  }

  @SuggestionList
  fun suggestions(vararg suggestions: String, block: List<FullLineLookupElement>.() -> Unit = {}) {
    if (myFixture.lookupElements == null) {
      patchProvider(*suggestions)
      myFixture.complete(CompletionType.BASIC)
    }

    block(myFixture.lookupElements!!.filterIsInstance<FullLineLookupElement>())
  }

  private fun selectAndCheck(expected: String, completionChar: Char, suggestion: String?) {
    myFixture.lookup.currentItem = myFixture.lookupElements
      ?.filterIsInstance<FullLineLookupElement>()
      ?.find { it.lookupString + it.suffix == pickingSuggestion }
    if (suggestion != null) {
      patchProvider(suggestion)
      pickingSuggestion = currentLine.toString() + expected + suggestion
    }

    myFixture.finishLookup(completionChar)

    val addedText = TextRange(completionStartOffset + currentLine.length, myFixture.caretOffset).substring(myFixture.file.text)
    assertEquals(expected, addedText, "Passed text does not equals to expected")
    currentLine.append(addedText)
  }

  // Patch provider's variants to disable auto-completion (for testing different behaviors)
  private fun patchProvider(vararg suggestions: String) {
    TestFullLineCompletionProvider.variants.clear()
    TestFullLineCompletionProvider.variants.addAll(suggestions.mapIndexed { i, it ->
      RawFullLineProposal(it, 1.0 - i / 100, FullLineProposal.BasicSyntaxCorrectness.CORRECT)
    })
  }
}
