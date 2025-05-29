// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbol
import com.intellij.webSymbols.PolySymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.webSymbols.PolySymbolQualifiedKind
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.impl.canUnwrapSymbols
import com.intellij.webSymbols.patterns.ComplexPatternOptions
import com.intellij.webSymbols.patterns.PolySymbolsPattern
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.query.PolySymbolMatch
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.webTypes.WebTypesJsonOrigin
import com.intellij.webSymbols.webTypes.json.*

internal fun NamePatternRoot.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): PolySymbolsPattern =
  when (val value = value) {
    is String -> RegExpPattern(value)
    is NamePatternBase -> value.wrap(defaultDisplayName, origin)
    else -> throw IllegalArgumentException(value::class.java.name)
  }

internal fun NamePatternBase.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): PolySymbolsPattern =
  when (this) {
    is NamePatternRegex -> RegExpPattern(regex, caseSensitive == true)
    is NamePatternDefault -> ComplexPattern(WebTypesComplexPatternConfigProvider(this, defaultDisplayName, origin))
    else -> throw IllegalArgumentException(this::class.java.name)
  }

@Suppress("UNCHECKED_CAST")
internal fun NamePatternTemplate.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): PolySymbolsPattern =
  when (val value = value) {
    is String -> when {
      value == "#item" -> SymbolReferencePattern(defaultDisplayName)
      value.startsWith("#item:") -> SymbolReferencePattern(value.substring(6))
      value == "#..." -> CompletionAutoPopupPattern(false)
      value == "$..." -> CompletionAutoPopupPattern(true)
      else -> StaticPattern(value)
    }
    is NamePatternBase -> value.wrap(defaultDisplayName, origin)
    is List<*> -> SequencePattern(SequencePatternPatternsProvider(value as List<NamePatternTemplate>, origin))
    else -> throw IllegalArgumentException(value::class.java.name)
  }

private class SequencePatternPatternsProvider(private val list: List<NamePatternTemplate>,
                                              private val origin: WebTypesJsonOrigin) : () -> List<PolySymbolsPattern> {
  override fun invoke(): List<PolySymbolsPattern> =
    list.map { it.wrap(null, origin) }
}

private class WebTypesComplexPatternConfigProvider(private val pattern: NamePatternDefault,
                                                   private val defaultDisplayName: String?,
                                                   private val origin: WebTypesJsonOrigin) : ComplexPatternConfigProvider {

  override fun getPatterns(): List<PolySymbolsPattern> =
    pattern.or.asSequence()
      .map { it.wrap(defaultDisplayName, origin) }
      .let {
        val template = pattern.template
        if (template.isEmpty()) {
          it
        }
        else {
          it.plus(SequencePattern(SequencePatternPatternsProvider(template, origin)))
        }
      }
      .toList()
      .ifEmpty { listOf(SequencePattern(SymbolReferencePattern(defaultDisplayName))) }


  override val isStaticAndRequired: Boolean
    get() = pattern.delegate == null && pattern.items == null && pattern.required != false

  override fun getOptions(queryExecutor: WebSymbolsQueryExecutor,
                          scopeStack: Stack<PolySymbolsScope>): ComplexPatternOptions {
    val queryParams = WebSymbolsNameMatchQueryParams.create(queryExecutor, true, false)
    val delegate = pattern.delegate?.resolve(scopeStack, queryParams.queryExecutor)?.firstOrNull()

    // Allow delegate pattern to override settings
    val apiStatus = delegate?.apiStatus?.takeIf { it.isDeprecatedOrObsolete() }
                    ?: pattern.toApiStatus(origin)?.takeIf { it.isDeprecatedOrObsolete() }
    val isRequired = (delegate?.required ?: pattern.required) != false
    val priority = delegate?.priority ?: pattern.priority?.wrap()
    val proximity = delegate?.proximity ?: pattern.proximity
    val repeats = pattern.repeat == true
    val unique = pattern.unique != false

    val symbolsResolver = createSymbolsResolver(delegate)
    return ComplexPatternOptions(delegate, apiStatus, isRequired, priority, proximity, repeats, unique, symbolsResolver)
  }

  private fun createSymbolsResolver(delegate: PolySymbol?) =
    if (pattern.delegate != null) {
      if (delegate != null) {
        PatternDelegateSymbolsResolver(delegate)
      }
      else null
    }
    else
      pattern.items
        ?.takeIf { it.isNotEmpty() }
        ?.let {
          PatternSymbolsResolver(it)
        }

  private class PatternDelegateSymbolsResolver(override val delegate: PolySymbol) : com.intellij.webSymbols.patterns.PolySymbolsPatternSymbolsResolver {
    override fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
      setOf(PolySymbolQualifiedKind(delegate.namespace, delegate.kind))

    override fun codeCompletion(name: String,
                                position: Int,
                                scopeStack: Stack<PolySymbolsScope>,
                                queryExecutor: WebSymbolsQueryExecutor): List<PolySymbolCodeCompletionItem> =
      delegate.pattern
        ?.complete(delegate, scopeStack,
                   this, CompletionParameters(name, queryExecutor, position), 0, name.length)
        ?.items
        ?.applyIcons(delegate)
      ?: emptyList()

    override fun listSymbols(scopeStack: Stack<PolySymbolsScope>,
                             queryExecutor: WebSymbolsQueryExecutor,
                             expandPatterns: Boolean): List<PolySymbol> =
      delegate.pattern
        ?.list(delegate, scopeStack, this, ListParameters(queryExecutor, expandPatterns))
        ?.flatMap { listResult ->
          if (listResult.segments.size == 1
              && listResult.segments[0].canUnwrapSymbols()) {
            listResult.segments[0].symbols
          }
          else {
            val lastContribution = scopeStack.peek() as PolySymbol
            listOf(PolySymbolMatch.create(listResult.name, listResult.segments,
                                          lastContribution.namespace, SPECIAL_MATCHED_CONTRIB,
                                          lastContribution.origin))
          }
        }
      ?: emptyList()

    override fun matchName(name: String, scopeStack: Stack<PolySymbolsScope>, queryExecutor: WebSymbolsQueryExecutor): List<PolySymbol> =
      delegate.pattern
        ?.match(delegate, scopeStack, null,
                MatchParameters(name, queryExecutor), 0, name.length)
        ?.flatMap { matchResult ->
          if (matchResult.start == matchResult.end) {
            emptySequence()
          }
          else if (matchResult.segments.size == 1
                   && matchResult.segments[0].canUnwrapSymbols()) {
            matchResult.segments[0].symbols.asSequence()
          }
          else {
            val lastContribution = scopeStack.peek() as PolySymbol
            sequenceOf(PolySymbolMatch.create(name, matchResult.segments,
                                              lastContribution.namespace, SPECIAL_MATCHED_CONTRIB,
                                              lastContribution.origin))
          }
        }
      ?: emptyList()

  }

  private class PatternSymbolsResolver(val items: ListReference) : com.intellij.webSymbols.patterns.PolySymbolsPatternSymbolsResolver {
    override fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
      items.asSequence().mapNotNull { it.getSymbolKind(context) }.toSet()

    override val delegate: PolySymbol?
      get() = null

    override fun codeCompletion(name: String,
                                position: Int,
                                scopeStack: Stack<PolySymbolsScope>,
                                queryExecutor: WebSymbolsQueryExecutor): List<PolySymbolCodeCompletionItem> =
      items.flatMap { it.codeCompletion(name, scopeStack, queryExecutor, position) }

    override fun listSymbols(scopeStack: Stack<PolySymbolsScope>,
                             queryExecutor: WebSymbolsQueryExecutor,
                             expandPatterns: Boolean): List<PolySymbol> =
      items.flatMap { it.list(scopeStack, queryExecutor, expandPatterns) }

    override fun matchName(name: String, scopeStack: Stack<PolySymbolsScope>, queryExecutor: WebSymbolsQueryExecutor): List<PolySymbol> =
      items.flatMap { it.resolve(name, scopeStack, queryExecutor) }

  }
}
