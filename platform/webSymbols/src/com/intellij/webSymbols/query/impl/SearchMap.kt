// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.impl.filterByQueryParams
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.*
import com.intellij.webSymbols.utils.match
import com.intellij.webSymbols.utils.toCodeCompletionItems
import com.intellij.webSymbols.utils.withMatchedName
import java.util.*

internal abstract class SearchMap<T> internal constructor(
  private val namesProvider: WebSymbolNamesProvider) {

  private val patterns: TreeMap<SearchMapEntry, MutableList<T>> = TreeMap()
  private val statics: TreeMap<SearchMapEntry, MutableList<T>> = TreeMap()

  internal abstract fun Sequence<T>.mapAndFilter(params: WebSymbolsQueryParams): Sequence<WebSymbol>

  internal fun add(qualifiedName: WebSymbolQualifiedName,
                   pattern: WebSymbolsPattern?,
                   item: T) {
    if (pattern == null) {
      namesProvider.getNames(qualifiedName, WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE)
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

  internal fun getMatchingSymbols(qualifiedName: WebSymbolQualifiedName,
                                  params: WebSymbolsNameMatchQueryParams,
                                  scope: Stack<WebSymbolsScope>): Sequence<WebSymbol> =
    namesProvider.getNames(qualifiedName, WebSymbolNamesProvider.Target.NAMES_QUERY)
      .asSequence()
      .mapNotNull { statics[SearchMapEntry(qualifiedName.qualifiedKind, it)] }
      .flatMapWithQueryParameters(params)
      .map { it.withMatchedName(qualifiedName.name) }
      .plus(collectPatternContributions(qualifiedName, params, scope))

  internal fun getSymbols(qualifiedKind: WebSymbolQualifiedKind, params: WebSymbolsListSymbolsQueryParams): Sequence<WebSymbolsScope> =
    statics.subMap(SearchMapEntry(qualifiedKind), SearchMapEntry(qualifiedKind, kindExclusive = true))
      .values.asSequence()
      .plus(patterns.subMap(SearchMapEntry(qualifiedKind), SearchMapEntry(qualifiedKind, kindExclusive = true)).values)
      .distinct()
      .flatMapWithQueryParameters(params)

  internal fun getCodeCompletions(qualifiedName: WebSymbolQualifiedName,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<WebSymbolsScope>): Sequence<WebSymbolCodeCompletionItem> =
    collectStaticCompletionResults(qualifiedName, params, scope)
      .asSequence()
      .plus(collectPatternCompletionResults(qualifiedName, params, scope))
      .distinct()

  private fun collectStaticCompletionResults(qualifiedName: WebSymbolQualifiedName,
                                             params: WebSymbolsCodeCompletionQueryParams,
                                             scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    statics.subMap(SearchMapEntry(qualifiedName.qualifiedKind), SearchMapEntry(qualifiedName.qualifiedKind, kindExclusive = true))
      .values
      .asSequence()
      .flatMapWithQueryParameters(params)
      .filter { !it.extension && !it.abstract && (!it.virtual || params.virtualSymbols) }
      .flatMap { it.toCodeCompletionItems(qualifiedName.name, params, scope) }
      .toList()

  private fun collectPatternCompletionResults(qualifiedName: WebSymbolQualifiedName,
                                              params: WebSymbolsCodeCompletionQueryParams,
                                              scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    patterns.subMap(SearchMapEntry(qualifiedName.qualifiedKind), SearchMapEntry(qualifiedName.qualifiedKind, kindExclusive = true))
      .values.asSequence()
      .flatMap { it.asSequence() }
      .distinct()
      .innerMapAndFilter(params)
      .filter { !it.extension && !it.abstract && (!it.virtual || params.virtualSymbols) }
      .flatMap { it.toCodeCompletionItems(qualifiedName.name, params, scope) }
      .toList()

  private fun collectPatternContributions(qualifiedName: WebSymbolQualifiedName,
                                          params: WebSymbolsNameMatchQueryParams,
                                          scope: Stack<WebSymbolsScope>): List<WebSymbol> =
    collectPatternsToProcess(qualifiedName)
      .let {
        if (it.size > 2)
          it.asSequence().distinct()
        else
          it.asSequence()
      }
      .innerMapAndFilter(params)
      .flatMap { rootContribution ->
        rootContribution.match(qualifiedName.name, params, scope)
      }
      .toList()

  private fun collectPatternsToProcess(qualifiedName: WebSymbolQualifiedName): Collection<T> {
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

  private fun Sequence<T>.innerMapAndFilter(params: WebSymbolsQueryParams): Sequence<WebSymbol> =
    mapAndFilter(params)
      .filterByQueryParams(params)


  private fun Sequence<Collection<T>>.flatMapWithQueryParameters(params: WebSymbolsQueryParams): Sequence<WebSymbol> =
    this.flatMap { it.asSequence().innerMapAndFilter(params) }

  private data class SearchMapEntry(val namespace: SymbolNamespace,
                                    val kind: SymbolKind,
                                    val name: CharSequence = "",
                                    val kindExclusive: Boolean = false) : Comparable<SearchMapEntry> {

    constructor(qualifiedKind: WebSymbolQualifiedKind, name: CharSequence = "", kindExclusive: Boolean = false) :
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

