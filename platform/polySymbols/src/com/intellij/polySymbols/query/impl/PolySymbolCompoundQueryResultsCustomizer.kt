// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query.impl

import com.intellij.model.Pointer
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.query.PolySymbolQueryResultsCustomizer

internal class PolySymbolCompoundQueryResultsCustomizer(private val customizers: List<PolySymbolQueryResultsCustomizer>) : PolySymbolQueryResultsCustomizer {

  override fun apply(
    matches: List<PolySymbol>, strict: Boolean,
    qualifiedName: PolySymbolQualifiedName,
  ): List<PolySymbol> =
    customizers.foldRight(matches) { scope, list ->
      scope.apply(list, strict, qualifiedName)
    }

  override fun apply(
    item: PolySymbolCodeCompletionItem,
    qualifiedKind: PolySymbolQualifiedKind,
  ): PolySymbolCodeCompletionItem? =
    customizers.foldRight(item as PolySymbolCodeCompletionItem?) { scope, i ->
      i?.let { scope.apply(it, qualifiedKind) }
    }

  override fun createPointer(): Pointer<out PolySymbolQueryResultsCustomizer> {
    val customizersPointers = customizers.map { it.createPointer() }
    return Pointer {
      val customizers = customizersPointers.map { it.dereference() }
      if (customizers.any { it == null }) return@Pointer null
      @Suppress("UNCHECKED_CAST")
      (PolySymbolCompoundQueryResultsCustomizer(customizers as List<PolySymbolQueryResultsCustomizer>))
    }
  }

  override fun getModificationCount(): Long =
    customizers.sumOf { it.modificationCount }

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is PolySymbolCompoundQueryResultsCustomizer
    && other.customizers == customizers

  override fun hashCode(): Int =
    customizers.hashCode()

}