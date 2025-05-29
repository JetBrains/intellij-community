// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.PolySymbolQualifiedName
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.query.PolySymbolsQueryResultsCustomizer

internal class PolySymbolsCompoundQueryResultsCustomizer(private val customizers: List<PolySymbolsQueryResultsCustomizer>) : PolySymbolsQueryResultsCustomizer {

  override fun apply(matches: List<PolySymbol>, strict: Boolean,
                     qualifiedName: PolySymbolQualifiedName): List<PolySymbol> =
    customizers.foldRight(matches) { scope, list ->
      scope.apply(list, strict, qualifiedName)
    }

  override fun apply(item: PolySymbolCodeCompletionItem,
                     qualifiedKind: PolySymbolQualifiedKind): PolySymbolCodeCompletionItem? =
    customizers.foldRight(item as PolySymbolCodeCompletionItem?) { scope, i ->
      i?.let { scope.apply(it, qualifiedKind) }
    }

  override fun createPointer(): Pointer<out PolySymbolsQueryResultsCustomizer> {
    val customizersPointers = customizers.map { it.createPointer() }
    return Pointer {
      val customizers = customizersPointers.map { it.dereference() }
      if (customizers.any { it == null }) return@Pointer null
      @Suppress("UNCHECKED_CAST")
      (PolySymbolsCompoundQueryResultsCustomizer(customizers as List<PolySymbolsQueryResultsCustomizer>))
    }
  }

  override fun getModificationCount(): Long =
    customizers.sumOf { it.modificationCount }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is PolySymbolsCompoundQueryResultsCustomizer
    && other.customizers == customizers

  override fun hashCode(): Int =
    customizers.hashCode()

}