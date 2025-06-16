// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.*
import com.intellij.polySymbols.utils.match
import com.intellij.polySymbols.utils.toCodeCompletionItems
import com.intellij.polySymbols.utils.withMatchedName
import com.intellij.util.SmartList
import com.intellij.util.text.CharSequenceSubSequence
import java.util.*

internal abstract class SearchMap<T> internal constructor(
  private val namesProvider: PolySymbolNamesProvider,
) {

  private val patterns: TreeMap<SearchMapEntry, MutableList<T>> = TreeMap()
  private val statics: TreeMap<SearchMapEntry, MutableList<T>> = TreeMap()

  internal abstract fun Sequence<T>.mapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol>

  internal fun add(
    qualifiedName: PolySymbolQualifiedName,
    pattern: PolySymbolPattern?,
    item: T,
  ) {
    if (pattern == null) {
      namesProvider.getNames(qualifiedName, PolySymbolNamesProvider.Target.NAMES_MAP_STORAGE)
        .forEach {
          statics.computeIfAbsent(SearchMapEntry(qualifiedName.qualifiedKind, it)) { SmartList() }.add(item)
        }

    }
    else
      pattern.getStaticPrefixes()
        .toSet()
        .let {
          if (it.isEmpty() || it.contains(""))
            setOf("")
          else it
        }
        .forEach {
          patterns.computeIfAbsent(SearchMapEntry(qualifiedName.qualifiedKind, it)) { SmartList() }.add(item)
        }
  }

  internal fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): Sequence<PolySymbol> =
    namesProvider.getNames(qualifiedName, PolySymbolNamesProvider.Target.NAMES_QUERY)
      .asSequence()
      .mapNotNull { statics[SearchMapEntry(qualifiedName.qualifiedKind, it)] }
      .flatMapWithQueryParameters(params)
      .map { it.withMatchedName(qualifiedName.name) }
      .plus(collectPatternContributions(qualifiedName, params, stack))

  internal fun getSymbols(qualifiedKind: PolySymbolQualifiedKind, params: PolySymbolListSymbolsQueryParams): Sequence<PolySymbol> =
    statics.subMap(SearchMapEntry(qualifiedKind), SearchMapEntry(qualifiedKind, kindExclusive = true))
      .values.asSequence()
      .plus(patterns.subMap(SearchMapEntry(qualifiedKind), SearchMapEntry(qualifiedKind, kindExclusive = true)).values)
      .distinct()
      .flatMapWithQueryParameters(params)

  internal fun getCodeCompletions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): Sequence<PolySymbolCodeCompletionItem> =
    collectStaticCompletionResults(qualifiedName, params, stack)
      .asSequence()
      .plus(collectPatternCompletionResults(qualifiedName, params, stack))
      .distinct()

  private fun collectStaticCompletionResults(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    statics.subMap(SearchMapEntry(qualifiedName.qualifiedKind), SearchMapEntry(qualifiedName.qualifiedKind, kindExclusive = true))
      .values
      .asSequence()
      .flatMapWithQueryParameters(params)
      .filter { !it.extension && params.accept(it) }
      .flatMap { it.toCodeCompletionItems(qualifiedName.name, params, stack) }
      .toList()

  private fun collectPatternCompletionResults(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolCodeCompletionQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbolCodeCompletionItem> =
    patterns.subMap(SearchMapEntry(qualifiedName.qualifiedKind), SearchMapEntry(qualifiedName.qualifiedKind, kindExclusive = true))
      .values.asSequence()
      .flatMap { it.asSequence() }
      .distinct()
      .innerMapAndFilter(params)
      .filter { !it.extension && params.accept(it) }
      .flatMap { it.toCodeCompletionItems(qualifiedName.name, params, stack) }
      .toList()

  private fun collectPatternContributions(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): List<PolySymbol> =
    collectPatternsToProcess(qualifiedName)
      .let {
        if (it.size > 2)
          it.asSequence().distinct()
        else
          it.asSequence()
      }
      .innerMapAndFilter(params)
      .flatMap { rootContribution ->
        rootContribution.match(qualifiedName.name, params, stack)
      }
      .toList()

  private fun collectPatternsToProcess(qualifiedName: PolySymbolQualifiedName): Collection<T> {
    val toProcess = SmartList<T>()
    for (p in 0..qualifiedName.name.length) {
      val check = SearchMapEntry(qualifiedName.qualifiedKind, CharSequenceSubSequence(qualifiedName.name, 0, p))
      val entry = patterns.ceilingEntry(check)
      if (entry == null
          || entry.key.namespace != qualifiedName.namespace
          || entry.key.kind != qualifiedName.kind
          || !entry.key.name.startsWith(check.name)) break
      if (entry.key.name.length == p) toProcess.addAll(entry.value)
    }
    return toProcess
  }

  private fun Sequence<T>.innerMapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol> =
    mapAndFilter(params)
      .filterByQueryParams(params)


  private fun Sequence<Collection<T>>.flatMapWithQueryParameters(params: PolySymbolQueryParams): Sequence<PolySymbol> =
    this.flatMap { it.asSequence().innerMapAndFilter(params) }

  private data class SearchMapEntry(
    val namespace: PolySymbolNamespace,
    val kind: PolySymbolKind,
    val name: CharSequence = "",
    val kindExclusive: Boolean = false,
  ) : Comparable<SearchMapEntry> {

    constructor(qualifiedKind: PolySymbolQualifiedKind, name: CharSequence = "", kindExclusive: Boolean = false) :
      this(qualifiedKind.namespace, qualifiedKind.kind, name, kindExclusive)

    override fun compareTo(other: SearchMapEntry): Int {
      val namespaceCompare = namespace.compareTo(other.namespace)
      if (namespaceCompare != 0) return namespaceCompare
      val kindCompare = compareWithExclusive(kind, other.kind, kindExclusive, other.kindExclusive)
      if (kindCompare != 0) return kindCompare
      return StringUtil.compare(name, other.name, false)
    }

    private fun compareWithExclusive(a: CharSequence, b: CharSequence, aExcl: Boolean, bExcl: Boolean): Int {
      val result = StringUtil.compare(a, b, false)
      if (result != 0) return result
      if (aExcl != bExcl) {
        if (aExcl) return 1
        else return -1
      }
      return 0
    }

  }
}