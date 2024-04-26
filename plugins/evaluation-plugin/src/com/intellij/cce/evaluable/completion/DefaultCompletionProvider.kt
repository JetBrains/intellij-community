// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.evaluable.completion

import com.intellij.cce.core.CommonFeatures
import com.intellij.cce.core.Features
import com.intellij.cce.core.Lookup
import com.intellij.cce.evaluable.common.asSuggestion
import com.intellij.cce.evaluable.common.readActionInSmartMode
import com.intellij.cce.evaluation.SuggestionsProvider
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionProgressIndicator
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupEx
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.actions.MLCompletionFeaturesUtil
import com.intellij.completion.ml.util.prefix
import com.intellij.lang.Language
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor

class DefaultCompletionProvider : SuggestionsProvider {
  override val name: String = "DEFAULT"

  override fun getSuggestions(expectedText: String, editor: Editor, language: Language, comparator: (String, String) -> Boolean): Lookup = readActionInSmartMode(editor.project!!) {
    val start = System.currentTimeMillis()
    val isNew = LookupManager.getActiveLookup(editor) == null
    val activeLookup = LookupManager.getActiveLookup(editor) ?: invokeCompletion(editor)
    val latency = System.currentTimeMillis() - start
    if (activeLookup == null) {
      return@readActionInSmartMode Lookup.fromExpectedText(expectedText, "", emptyList(), latency,
                                                           isNew = isNew, startOffset = editor.caretModel.logicalPosition.column,
                                                           comparator = comparator)
    }

    val lookup = activeLookup as LookupImpl
    val features = MLCompletionFeaturesUtil.getCommonFeatures(lookup)
    val resultFeatures = Features(
      CommonFeatures(features.context, features.user, features.session),
      lookup.items.map { MLCompletionFeaturesUtil.getElementFeatures(lookup, it).features }
    )
    val suggestions = lookup.items.map { it.asSuggestion() }

    return@readActionInSmartMode Lookup.fromExpectedText(expectedText, lookup.prefix(), suggestions, latency, resultFeatures, isNew,
                                                         editor.caretModel.logicalPosition.column, comparator)
  }

  private fun invokeCompletion(editor: Editor): LookupEx? {
    val project = editor.project!!
    val handler = object : CodeCompletionHandlerBase(CompletionType.BASIC, false, false, true) {
      // Guarantees synchronous execution
      override fun isTestingCompletionQualityMode() = true
      override fun lookupItemSelected(indicator: CompletionProgressIndicator?,
                                      item: LookupElement,
                                      completionChar: Char,
                                      items: MutableList<LookupElement>?) {
        afterItemInsertion(indicator, null)
      }
    }
    try {
      handler.invokeCompletion(project, editor)
    }
    catch (e: AssertionError) {
      LOG.warn("Completion invocation ended with error", e)
    }
    return LookupManager.getActiveLookup(editor)
  }
}

private val LOG = logger<DefaultCompletionProvider>()
