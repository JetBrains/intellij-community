// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProcessEx
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.getAndUpdateUserData
import com.intellij.patterns.StandardPatterns
import com.intellij.polySymbols.FrameworkId
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItemCustomizer.Companion.customizeItems
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryExecutorFactory
import com.intellij.polySymbols.query.PolySymbolScope
import com.intellij.psi.PsiElement
import com.intellij.psi.util.startOffset
import com.intellij.util.ProcessingContext

abstract class PolySymbolsCompletionProviderBase<T : PsiElement> : CompletionProvider<CompletionParameters>() {
  final override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val psiContext = getContext(parameters.position.originalElement) ?: return
    val queryExecutor = PolySymbolQueryExecutorFactory.create(psiContext)

    val elementOffset = parameters.position.startOffset
    val position: Int
    val name: String

    if (parameters.offset > elementOffset) {
      position = parameters.offset - elementOffset
      val document = parameters.editor.document.let {
        if (it is DocumentWindow && !InjectedLanguageManager.getInstance(psiContext.project).isInjectedFragment(parameters.originalFile)) {
          // the completion process is run for the parameters.position from host file,
          // but the caret is inside injection, so let's get the correct document for the offsets
          it.delegate
        }
        else it
      }
      name = document.getText(TextRange(elementOffset, parameters.offset))
    }
    else {
      position = 0
      name = ""
    }
    addCompletions(parameters, result, position, name, queryExecutor, psiContext)
  }


  protected abstract fun getContext(position: PsiElement): T?

  protected abstract fun addCompletions(
    parameters: CompletionParameters, result: CompletionResultSet,
    position: Int, name: String, queryExecutor: PolySymbolQueryExecutor, context: T,
  )

  companion object {

    private val preventedCodeCompletionsKey = Key<Set<PolySymbolQualifiedKind>>("polySymbols.completion.preventedSymbolKinds")

    @JvmStatic
    fun preventFurtherCodeCompletionsFor(parameters: CompletionParameters, qualifiedKind: PolySymbolQualifiedKind) {
      (parameters.process as CompletionProcessEx).getAndUpdateUserData(preventedCodeCompletionsKey) {
        it?.let { it + qualifiedKind } ?: setOf(qualifiedKind)
      }
    }

    @JvmStatic
    fun isFurtherCodeCompletionPreventedFor(parameters: CompletionParameters, vararg qualifiedKind: PolySymbolQualifiedKind): Boolean =
      (parameters.process as CompletionProcessEx).getUserData(preventedCodeCompletionsKey)
        ?.let { prevented -> qualifiedKind.any { prevented.contains(it) } } == true

    @JvmStatic
    fun processCompletionQueryResults(
      queryExecutor: PolySymbolQueryExecutor,
      result: CompletionResultSet,
      qualifiedKind: PolySymbolQualifiedKind,
      name: String,
      position: Int,
      location: PsiElement,
      queryContext: List<PolySymbolScope> = emptyList(),
      providedNames: MutableSet<String>? = null,
      filter: ((PolySymbolCodeCompletionItem) -> Boolean)? = null,
      consumer: (PolySymbolCodeCompletionItem) -> Unit,
    ) {
      processPolySymbolCodeCompletionItems(
        queryExecutor.codeCompletionQuery(qualifiedKind, name, position).additionalScope(queryContext).run(),
        result, qualifiedKind, name, queryExecutor.framework, location, providedNames, filter, consumer
      )
    }

    @JvmStatic
    fun processPolySymbolCodeCompletionItems(
      symbols: List<PolySymbolCodeCompletionItem>,
      result: CompletionResultSet,
      qualifiedKind: PolySymbolQualifiedKind,
      name: String,
      framework: FrameworkId?,
      location: PsiElement,
      providedNames: MutableSet<String>? = null,
      filter: ((PolySymbolCodeCompletionItem) -> Boolean)? = null,
      consumer: (PolySymbolCodeCompletionItem) -> Unit,
    ) {
      val prefixLength = name.length
      val prefixes = mutableSetOf<String>()
      symbols
        .asSequence()
        .distinctBy { Triple(it.offset, it.name, it.completeAfterInsert) }
        .customizeItems(framework, qualifiedKind, location)
        .filter { item ->
          (filter == null || filter(item))
          && item.offset <= prefixLength
          && (providedNames == null || providedNames.add(name.substring(0, item.offset) + item.name))
        }
        .onEach { item ->
          if (item.completeAfterInsert) {
            val namePrefix = name.substring(0, item.offset)
            prefixes.add(namePrefix + item.name)
            prefixes.addAll(item.aliases.map { namePrefix + it })
          }
        }
        .forEach(consumer)
      result.restartCompletionOnPrefixChange(StandardPatterns.string().oneOf(prefixes))
    }
  }
}
