package com.intellij.cce.evaluable.testGeneration

import com.intellij.cce.core.*
import com.intellij.cce.evaluable.common.getEditorSafe
import com.intellij.cce.evaluation.SuggestionsProvider
import com.intellij.cce.interpreter.FeatureInvoker
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager

class TestGenerationInvoker(private val project: Project,
                            private val language: Language,
                            private val strategy: TestGenerationStrategy) : FeatureInvoker {
  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session
  {
    val editor = runReadAction {
      getEditorSafe(project)
    }
    runInEdt {
      PsiDocumentManager.getInstance(project).commitDocument(editor.document)
    }
    val session = Session(offset, expectedText, expectedText.length, TokenProperties.UNKNOWN)
    val lookup = getSuggestions(expectedText, editor, strategy.suggestionsProvider)

    val res = mapOf(
      "unitTest" to (properties as SimpleTokenProperties).additionalProperty("unitTest")!!,
      "testPath" to properties.additionalProperty("testPath")!!,
      "testOffset" to properties.additionalProperty("testOffset")!!
    )

    session.addLookup(Lookup.fromExpectedText("", "", lookup.suggestions, lookup.latency, null, false, additionalInfo = lookup.additionalInfo + res, comparator = this::comparator))
    return session
  }

  protected fun getSuggestions(expectedLine: String, editor: Editor, suggestionsProviderName: String): com.intellij.cce.core.Lookup {
    val lang = com.intellij.lang.Language.findLanguageByID(language.ideaLanguageId)
               ?: throw IllegalStateException("Can't find language \"${language.ideaLanguageId}\"")
    val provider = SuggestionsProvider.find(project, suggestionsProviderName)
                   ?: throw IllegalStateException("Can't find suggestions provider \"${suggestionsProviderName}\"")
    return provider.getSuggestions(expectedLine, editor, lang, this::comparator)
  }


  override fun comparator(generated: String, expected: String): Boolean = expected == generated
}
