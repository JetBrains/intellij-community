// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolKindName
import com.intellij.polySymbols.PolySymbolNamespace
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.query.PolySymbolCodeCompletionQueryParams
import com.intellij.polySymbols.query.PolySymbolListSymbolsQueryParams
import com.intellij.polySymbols.query.PolySymbolNameMatchQueryParams
import com.intellij.polySymbols.query.PolySymbolNamesProvider
import com.intellij.polySymbols.query.PolySymbolQueryParams
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.utils.match
import com.intellij.polySymbols.utils.toCodeCompletionItems
import com.intellij.polySymbols.utils.withMatchedName
import com.intellij.util.SmartList
import com.intellij.util.text.CharSequenceSubSequence
import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap

internal abstract class SearchMap<T> internal constructor(
  private val namesProvider: PolySymbolNamesProvider,
  useSyncMap: Boolean = false,
) {

  private val patterns: NavigableMap<SearchMapEntry, MutableList<T>> =
    if (useSyncMap) Collections.synchronizedNavigableMap(TreeMap()) else TreeMap()

  private val statics: NavigableMap<SearchMapEntry, MutableList<T>> =
    if (useSyncMap) Collections.synchronizedNavigableMap(TreeMap()) else TreeMap()

  internal abstract fun Sequence<T>.mapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol>

  internal fun add(
    qualifiedName: PolySymbolQualifiedName,
    pattern: PolySymbolPattern?,
    item: T,
  ) {
    if (pattern == null) {
      namesProvider.getNames(qualifiedName, PolySymbolNamesProvider.Target.NAMES_MAP_STORAGE)
        .forEach {
          statics.computeIfAbsent(SearchMapEntry(qualifiedName.kind, it)) { SmartList() }.add(item)
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
          patterns.computeIfAbsent(SearchMapEntry(qualifiedName.kind, it)) { SmartList() }.add(item)
        }
  }

  internal fun getMatchingSymbols(
    qualifiedName: PolySymbolQualifiedName,
    params: PolySymbolNameMatchQueryParams,
    stack: PolySymbolQueryStack,
  ): Sequence<PolySymbol> =
    namesProvider.getNames(qualifiedName, PolySymbolNamesProvider.Target.NAMES_QUERY)
      .asSequence()
      .mapNotNull { statics[SearchMapEntry(qualifiedName.kind, it)] }
      .flatMapWithQueryParameters(params)
      .map { it.withMatchedName(qualifiedName.name) }
      .plus(collectPatternContributions(qualifiedName, params, stack))

  internal fun getSymbols(kind: PolySymbolKind, params: PolySymbolListSymbolsQueryParams): Sequence<PolySymbol> =
    statics.subMap(SearchMapEntry(kind), SearchMapEntry(kind, kindExclusive = true))
      .values.asSequence()
      .plus(patterns.subMap(SearchMapEntry(kind), SearchMapEntry(kind, kindExclusive = true)).values)
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
    statics.subMap(SearchMapEntry(qualifiedName.kind), SearchMapEntry(qualifiedName.kind, kindExclusive = true))
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
    patterns.subMap(SearchMapEntry(qualifiedName.kind), SearchMapEntry(qualifiedName.kind, kindExclusive = true))
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
  ): Sequence<PolySymbol> =
    collectPatternsToProcess(qualifiedName)
      .innerMapAndFilter(params)
      .flatMap { rootContribution ->
        rootContribution.match(qualifiedName.name, params, stack)
      }

  private fun collectPatternsToProcess(qualifiedName: PolySymbolQualifiedName): Sequence<T> {
    var singleResult: T? = null
    var multipleResults: MutableSet<T>? = null
    var size = 0
    for (p in 0..qualifiedName.name.length) {
      val check = SearchMapEntry(qualifiedName.kind, CharSequenceSubSequence(qualifiedName.name, 0, p))
      val entry = patterns.ceilingEntry(check)
      if (entry == null
          || entry.key.namespace != qualifiedName.namespace
          || entry.key.kindName != qualifiedName.kind.kindName
          || !entry.key.name.startsWith(check.name)) break
      if (entry.key.name.length == p && entry.value.isNotEmpty()) {
        size += entry.value.size
        if (size > 1) {
          if (multipleResults == null) {
            multipleResults = LinkedHashSet()
            if (singleResult != null) {
              multipleResults.add(singleResult)
              singleResult = null
            }
          }
          multipleResults.addAll(entry.value)
        }
        else {
          singleResult = entry.value.first()
        }
      }
    }
    return multipleResults?.asSequence()
           ?: singleResult?.let { sequenceOf(singleResult) }
           ?: emptySequence()
  }

  private fun Sequence<T>.innerMapAndFilter(params: PolySymbolQueryParams): Sequence<PolySymbol> =
    mapAndFilter(params)
      .filterByQueryParams(params)


  private fun Sequence<Collection<T>>.flatMapWithQueryParameters(params: PolySymbolQueryParams): Sequence<PolySymbol> =
    this.flatMap { it.asSequence().innerMapAndFilter(params) }

  private data class SearchMapEntry(
    val namespace: PolySymbolNamespace,
    val kindName: PolySymbolKindName,
    val name: CharSequence = "",
    val kindExclusive: Boolean = false,
  ) : Comparable<SearchMapEntry> {

    constructor(kind: PolySymbolKind, name: CharSequence = "", kindExclusive: Boolean = false) :
      this(kind.namespace, kind.kindName, name, kindExclusive)

    override fun compareTo(other: SearchMapEntry): Int {
      val namespaceCompare = namespace.compareTo(other.namespace)
      if (namespaceCompare != 0) return namespaceCompare
      val kindCompare = compareWithExclusive(kindName, other.kindName, kindExclusive, other.kindExclusive)
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