// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.golf


import com.intellij.cce.actions.TextRange
import com.intellij.cce.core.*
import com.intellij.cce.evaluable.common.CommonActionsInvoker
import com.intellij.cce.evaluable.common.asSuggestion
import com.intellij.cce.evaluable.common.getEditorSafe
import com.intellij.cce.evaluable.common.readActionInSmartMode
import com.intellij.cce.evaluable.completion.BaseCompletionActionsInvoker
import com.intellij.cce.evaluation.SuggestionsProvider
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.actions.MLCompletionFeaturesUtil
import com.intellij.completion.ml.util.prefix
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.util.progress.sleepCancellable
import kotlin.random.Random

class LineCompletionActionsInvoker(project: Project,
                                   language: Language,
                                   private val strategy: CompletionGolfStrategy,
                                   private val isBenchmark: Boolean) : BaseCompletionActionsInvoker(project, language) {
  private val commonInvoker = CommonActionsInvoker(project)
  private var benchmarkRandom = resetRandom()
  private var isFirst = true

  override fun callFeature(expectedText: String, offset: Int, properties: TokenProperties): Session = readActionInSmartMode(project) {
    val editor = getEditorSafe(project)
    val lineProperties = properties as LineProperties
    val ranges = lineProperties.completableRanges
    val session = Session(offset, expectedText, ranges.sumOf { it.end - it.start }, TokenProperties.UNKNOWN)
    if (isBenchmark) {
      session.benchmark(expectedText, ranges, offset, editor)
    }
    else {
      session.emulateCG(expectedText, ranges, offset, editor)
    }
    return@readActionInSmartMode session
  }

  private fun Session.benchmark(expectedLine: String,
                                completableRanges: List<TextRange>,
                                offset: Int,
                                editor: Editor) {

    val emulator = CompletionGolfEmulation.createFromStrategy(strategy, expectedLine)
    for (range in completableRanges) {
      val prefixLength = benchmarkRandom.nextInt(range.end - range.start)
      commonInvoker.moveCaret(range.start + prefixLength)
      val lookup = getSuggestions(expectedLine, editor)
      emulator.pickBestSuggestion(expectedLine.substring(0, range.start - offset + prefixLength), lookup, this).also {
        LookupManager.hideActiveLookup(project)
        addLookup(it)
      }
    }
  }

  private fun Session.emulateCG(expectedLine: String, completableRanges: List<TextRange>, offset: Int, editor: Editor) {
    val emulator = CompletionGolfEmulation.createFromStrategy(strategy, expectedLine)
    var currentString = ""
    while (currentString != expectedLine) {
      val nextChar = expectedLine[currentString.length].toString()
      if (!completableRanges.any { offset + currentString.length >= it.start && offset + currentString.length < it.end }) {
        currentString += nextChar
        continue
      }

      commonInvoker.moveCaret(offset + currentString.length)
      val lookup = getSuggestions(expectedLine, editor)

      emulator.pickBestSuggestion(currentString, lookup, this).also { resultLookup ->
        val selected = resultLookup.selectedWithoutPrefix()
        val lookupImpl = LookupManager.getActiveLookup(editor) as? LookupImpl
        if (selected != null && lookupImpl != null) {
          lookupImpl.finish(resultLookup.selectedPosition, selected.length, forceUndo = true)
        }
        if (strategy.invokeOnEachChar) {
          LookupManager.hideActiveLookup(project)
        }
        currentString += selected ?: nextChar
        if (currentString.isNotEmpty() && !strategy.invokeOnEachChar) {
          if (resultLookup.suggestions.isEmpty() || currentString.last().let { ch -> !(ch == '_' || ch.isLetter() || ch.isDigit()) }) {
            LookupManager.hideActiveLookup(project)
          }
        }
        addLookup(resultLookup)
      }
    }
  }

  private fun getSuggestions(expectedLine: String, editor: Editor): Lookup {
    if (strategy.isDefaultProvider()) {
      if (isFirst) {
        callCompletion(expectedLine, null, editor)
        sleepCancellable(10000)
        isFirst = false
      }
      return callCompletion(expectedLine, null, editor)
    }
    val lang = com.intellij.lang.Language.findLanguageByID(language.ideaLanguageId)
               ?: throw IllegalStateException("Can't find language \"${language.ideaLanguageId}\"")
    val provider = SuggestionsProvider.find(project, strategy.suggestionsProvider)
                   ?: throw IllegalStateException("Can't find suggestions provider \"${strategy.suggestionsProvider}\"")
    return provider.getSuggestions(expectedLine, editor, lang)
  }

  private fun callCompletion(expectedText: String, prefix: String?, editor: Editor): Lookup {
    val start = System.currentTimeMillis()
    val isNew = LookupManager.getActiveLookup(editor) == null
    val activeLookup = LookupManager.getActiveLookup(editor) ?: invokeCompletion(expectedText, prefix, CompletionType.BASIC, editor)
    val latency = System.currentTimeMillis() - start
    if (activeLookup == null) {
      return Lookup.fromExpectedText(expectedText, prefix ?: "", emptyList(), latency, isNew = isNew)
    }

    val lookup = activeLookup as LookupImpl
    val features = MLCompletionFeaturesUtil.getCommonFeatures(lookup)
    val resultFeatures = Features(
      CommonFeatures(features.context, features.user, features.session),
      lookup.items.map { MLCompletionFeaturesUtil.getElementFeatures(lookup, it).features }
    )
    val suggestions = lookup.items.map { it.asSuggestion() }

    return Lookup.fromExpectedText(expectedText, lookup.prefix(), suggestions, latency, resultFeatures, isNew)
  }

  private fun resetRandom(): Random = Random(BENCHMARK_RANDOM_SEED)
}

private const val BENCHMARK_RANDOM_SEED = 0
