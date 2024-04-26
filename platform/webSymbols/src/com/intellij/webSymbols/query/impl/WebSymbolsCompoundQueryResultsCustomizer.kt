// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolQualifiedName
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.query.WebSymbolsQueryResultsCustomizer

internal class WebSymbolsCompoundQueryResultsCustomizer(private val customizers: List<WebSymbolsQueryResultsCustomizer>) : WebSymbolsQueryResultsCustomizer {

  override fun apply(matches: List<WebSymbol>, strict: Boolean,
                     qualifiedName: WebSymbolQualifiedName): List<WebSymbol> =
    customizers.foldRight(matches) { scope, list ->
      scope.apply(list, strict, qualifiedName)
    }

  override fun apply(item: WebSymbolCodeCompletionItem,
                     qualifiedKind: WebSymbolQualifiedKind): WebSymbolCodeCompletionItem? =
    customizers.foldRight(item as WebSymbolCodeCompletionItem?) { scope, i ->
      i?.let { scope.apply(it, qualifiedKind) }
    }

  override fun createPointer(): Pointer<out WebSymbolsQueryResultsCustomizer> {
    val customizersPointers = customizers.map { it.createPointer() }
    return Pointer {
      val customizers = customizersPointers.map { it.dereference() }
      if (customizers.any { it == null }) return@Pointer null
      @Suppress("UNCHECKED_CAST")
      (WebSymbolsCompoundQueryResultsCustomizer(customizers as List<WebSymbolsQueryResultsCustomizer>))
    }
  }

  override fun getModificationCount(): Long =
    customizers.sumOf { it.modificationCount }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is WebSymbolsCompoundQueryResultsCustomizer
    && other.customizers == customizers

  override fun hashCode(): Int =
    customizers.hashCode()

}