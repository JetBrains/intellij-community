// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbol
import com.intellij.webSymbols.WebSymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.webSymbols.WebSymbolQualifiedKind
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.ComplexPatternOptions
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.query.WebSymbolMatch
import com.intellij.webSymbols.query.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.webTypes.WebTypesJsonOrigin
import com.intellij.webSymbols.webTypes.json.*

internal fun NamePatternRoot.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): WebSymbolsPattern =
  when (val value = value) {
    is String -> RegExpPattern(value)
    is NamePatternBase -> value.wrap(defaultDisplayName, origin)
    else -> throw IllegalArgumentException(value::class.java.name)
  }

internal fun NamePatternBase.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): WebSymbolsPattern =
  when (this) {
    is NamePatternRegex -> RegExpPattern(regex, caseSensitive == true)
    is NamePatternDefault -> ComplexPattern(WebTypesComplexPatternConfigProvider(this, defaultDisplayName, origin))
    else -> throw IllegalArgumentException(this::class.java.name)
  }

@Suppress("UNCHECKED_CAST")
internal fun NamePatternTemplate.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): WebSymbolsPattern =
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
                                              private val origin: WebTypesJsonOrigin) : () -> List<WebSymbolsPattern> {
  override fun invoke(): List<WebSymbolsPattern> =
    list.map { it.wrap(null, origin) }
}

private class WebTypesComplexPatternConfigProvider(private val pattern: NamePatternDefault,
                                                   private val defaultDisplayName: String?,
                                                   private val origin: WebTypesJsonOrigin) : ComplexPatternConfigProvider {

  override fun getPatterns(): List<WebSymbolsPattern> =
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
                          scopeStack: Stack<WebSymbolsScope>): ComplexPatternOptions {
    val queryParams = WebSymbolsNameMatchQueryParams(queryExecutor, true, false)
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

  private fun createSymbolsResolver(delegate: WebSymbol?) =
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

  private class PatternDelegateSymbolsResolver(override val delegate: WebSymbol) : com.intellij.webSymbols.patterns.WebSymbolsPatternSymbolsResolver {
    override fun getSymbolKinds(context: WebSymbol?): Set<WebSymbolQualifiedKind> =
      setOf(WebSymbolQualifiedKind(delegate.namespace, delegate.kind))

    override fun codeCompletion(name: String,
                                position: Int,
                                scopeStack: Stack<WebSymbolsScope>,
                                queryExecutor: WebSymbolsQueryExecutor): List<WebSymbolCodeCompletionItem> =
      delegate.pattern
        ?.complete(delegate, scopeStack,
                   this, CompletionParameters(name, queryExecutor, position), 0, name.length)
        ?.items
        ?.applyIcons(delegate)
      ?: emptyList()

    override fun listSymbols(scopeStack: Stack<WebSymbolsScope>,
                             queryExecutor: WebSymbolsQueryExecutor,
                             expandPatterns: Boolean): List<WebSymbol> =
      delegate.pattern
        ?.list(delegate, scopeStack, this, ListParameters(queryExecutor, expandPatterns))
        ?.flatMap { listResult ->
          if (listResult.segments.size == 1
              && listResult.segments[0].canUnwrapSymbols()) {
            listResult.segments[0].symbols
          }
          else {
            val lastContribution = scopeStack.peek() as WebSymbol
            listOf(WebSymbolMatch.create(listResult.name, listResult.segments,
                                         lastContribution.namespace, SPECIAL_MATCHED_CONTRIB,
                                         lastContribution.origin))
          }
        }
      ?: emptyList()

    override fun matchName(name: String, scopeStack: Stack<WebSymbolsScope>, queryExecutor: WebSymbolsQueryExecutor): List<WebSymbol> =
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
            val lastContribution = scopeStack.peek() as WebSymbol
            sequenceOf(WebSymbolMatch.create(name, matchResult.segments,
                                             lastContribution.namespace, SPECIAL_MATCHED_CONTRIB,
                                             lastContribution.origin))
          }
        }
      ?: emptyList()

  }

  private class PatternSymbolsResolver(val items: ListReference) : com.intellij.webSymbols.patterns.WebSymbolsPatternSymbolsResolver {
    override fun getSymbolKinds(context: WebSymbol?): Set<WebSymbolQualifiedKind> =
      items.asSequence().mapNotNull { it.getSymbolKind(context) }.toSet()

    override val delegate: WebSymbol?
      get() = null

    override fun codeCompletion(name: String,
                                position: Int,
                                scopeStack: Stack<WebSymbolsScope>,
                                queryExecutor: WebSymbolsQueryExecutor): List<WebSymbolCodeCompletionItem> =
      items.flatMap { it.codeCompletion(name, scopeStack, queryExecutor, position) }

    override fun listSymbols(scopeStack: Stack<WebSymbolsScope>,
                             queryExecutor: WebSymbolsQueryExecutor,
                             expandPatterns: Boolean): List<WebSymbol> =
      items.flatMap { it.list(scopeStack, queryExecutor, expandPatterns) }

    override fun matchName(name: String, scopeStack: Stack<WebSymbolsScope>, queryExecutor: WebSymbolsQueryExecutor): List<WebSymbol> =
      items.flatMap { it.resolve(name, scopeStack, queryExecutor) }

  }
}
