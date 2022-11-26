// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query.impl

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.util.containers.Stack
import com.intellij.util.text.CharSequenceSubSequence
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.impl.filterByQueryParams
import com.intellij.webSymbols.impl.toCodeCompletionItems
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.query.WebSymbolNamesProvider
import com.intellij.webSymbols.query.WebSymbolsCodeCompletionQueryParams
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryParams
import com.intellij.webSymbols.utils.match
import java.util.*

internal abstract class SearchMap<T> internal constructor(
  private val namesProvider: WebSymbolNamesProvider) {

  private val patterns: TreeMap<SearchMapEntry, MutableList<T>> = TreeMap()
  private val statics: TreeMap<SearchMapEntry, MutableList<T>> = TreeMap()

  internal abstract fun Sequence<T>.mapAndFilter(params: WebSymbolsQueryParams): Sequence<WebSymbol>

  internal fun add(namespace: SymbolNamespace,
                   kind: SymbolKind,
                   name: String,
                   pattern: WebSymbolsPattern?,
                   item: T) {
    if (pattern == null) {
      namesProvider.getNames(namespace, kind, name, WebSymbolNamesProvider.Target.NAMES_MAP_STORAGE)
        .forEach {
          statics.computeIfAbsent(SearchMapEntry(namespace, kind, it)) { SmartList() }.add(item)
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
          patterns.computeIfAbsent(SearchMapEntry(namespace, kind, it)) { SmartList() }.add(item)
        }
  }

  internal fun getSymbols(namespace: SymbolNamespace,
                          kind: SymbolKind,
                          name: String?,
                          params: WebSymbolsNameMatchQueryParams,
                          scope: Stack<WebSymbolsScope>): Sequence<WebSymbol> =
    if (name == null) {
      statics.subMap(SearchMapEntry(namespace, kind), SearchMapEntry(namespace, kind, kindExclusive = true))
        .values.asSequence()
        .plus(patterns.subMap(SearchMapEntry(namespace, kind), SearchMapEntry(namespace, kind, kindExclusive = true)).values)
        .distinct()
        .flatMapWithQueryParameters(params)
    }
    else {
      val names = namesProvider.getNames(namespace, kind, name, WebSymbolNamesProvider.Target.NAMES_QUERY)
      names.asSequence()
        .mapNotNull { statics[SearchMapEntry(namespace, kind, it)] }
        .flatMapWithQueryParameters(params)
        .plus(collectPatternContributions(namespace, kind, name, params, scope))
    }

  internal fun getCodeCompletions(namespace: SymbolNamespace,
                                  kind: String,
                                  name: String?,
                                  params: WebSymbolsCodeCompletionQueryParams,
                                  scope: Stack<WebSymbolsScope>): Sequence<WebSymbolCodeCompletionItem> =
    collectStaticCompletionResults(namespace, kind, name, params, scope)
      .asSequence()
      .plus(collectPatternCompletionResults(namespace, kind, name, params, scope))
      .distinct()

  private fun collectStaticCompletionResults(namespace: SymbolNamespace,
                                             kind: String,
                                             name: String?,
                                             params: WebSymbolsCodeCompletionQueryParams,
                                             scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    statics.subMap(SearchMapEntry(namespace, kind), SearchMapEntry(namespace, kind, kindExclusive = true))
      .values
      .asSequence()
      .flatMapWithQueryParameters(params)
      .filter { !it.extension && !it.abstract && (!it.virtual || params.virtualSymbols) }
      .flatMap { it.toCodeCompletionItems(name, params, scope) }
      .toList()

  private fun collectPatternCompletionResults(namespace: SymbolNamespace,
                                              kind: String,
                                              name: String?,
                                              params: WebSymbolsCodeCompletionQueryParams,
                                              scope: Stack<WebSymbolsScope>): List<WebSymbolCodeCompletionItem> =
    patterns.subMap(SearchMapEntry(namespace, kind), SearchMapEntry(namespace, kind, kindExclusive = true))
      .values.asSequence()
      .flatMap { it.asSequence() }
      .distinct()
      .innerMapAndFilter(params)
      .filter { !it.extension && !it.abstract && (!it.virtual || params.virtualSymbols) }
      .flatMap { it.toCodeCompletionItems(name, params, scope) }
      .toList()

  private fun collectPatternContributions(namespace: SymbolNamespace,
                                          kind: String,
                                          name: String?,
                                          params: WebSymbolsNameMatchQueryParams,
                                          scope: Stack<WebSymbolsScope>): List<WebSymbol> =
    collectPatternsToProcess(namespace, kind, name ?: "")
      .asSequence()
      .distinct()
      .innerMapAndFilter(params)
      .flatMap { rootContribution ->
        rootContribution.match(name ?: "", scope, params)
      }
      .toList()

  private fun collectPatternsToProcess(namespace: SymbolNamespace, kind: String, name: String): Collection<T> {
    val toProcess = mutableSetOf<T>()
    for (p in 0..name.length) {
      val check = SearchMapEntry(namespace, kind, CharSequenceSubSequence(name, 0, p))
      val entry = patterns.ceilingEntry(check)
      if (entry == null
          || entry.key.namespace != namespace
          || entry.key.kind != kind
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

