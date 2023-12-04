// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.injected.editor.DocumentWindow
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.StandardPatterns
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.ProcessingContext
import com.intellij.webSymbols.FrameworkId
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItemCustomizer.Companion.customizeItems
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.query.WebSymbolsQueryExecutorFactory

abstract class WebSymbolsCompletionProviderBase<T : PsiElement> : CompletionProvider<CompletionParameters>() {
  final override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
    val psiContext = getContext(parameters.position.originalElement) ?: return
    val queryExecutor = WebSymbolsQueryExecutorFactory.create(psiContext)

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

  protected abstract fun addCompletions(parameters: CompletionParameters, result: CompletionResultSet,
                                        position: Int, name: String, queryExecutor: WebSymbolsQueryExecutor, context: T)

  companion object {

    @JvmStatic
    fun processCompletionQueryResults(queryExecutor: WebSymbolsQueryExecutor,
                                      result: CompletionResultSet,
                                      qualifiedKind: WebSymbolQualifiedKind,
                                      name: String,
                                      position: Int,
                                      location: PsiElement,
                                      queryContext: List<WebSymbolsScope> = emptyList(),
                                      providedNames: MutableSet<String>? = null,
                                      filter: ((WebSymbolCodeCompletionItem) -> Boolean)? = null,
                                      consumer: (WebSymbolCodeCompletionItem) -> Unit) {
      processWebSymbolCodeCompletionItems(
        queryExecutor.runCodeCompletionQuery(qualifiedKind, name, position, scope = queryContext),
        result, qualifiedKind, name, queryExecutor.framework, location, providedNames, filter, consumer
      )
    }

    @JvmStatic
    fun processWebSymbolCodeCompletionItems(symbols: List<WebSymbolCodeCompletionItem>,
                                            result: CompletionResultSet,
                                            qualifiedKind: WebSymbolQualifiedKind,
                                            name: String,
                                            framework: FrameworkId?,
                                            location: PsiElement,
                                            providedNames: MutableSet<String>? = null,
                                            filter: ((WebSymbolCodeCompletionItem) -> Boolean)? = null,
                                            consumer: (WebSymbolCodeCompletionItem) -> Unit) {
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
