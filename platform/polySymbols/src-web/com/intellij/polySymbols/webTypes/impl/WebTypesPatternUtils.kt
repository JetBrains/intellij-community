// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.webTypes.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus.Companion.isDeprecatedOrObsolete
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.PolySymbolQualifiedKind
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.impl.canUnwrapSymbols
import com.intellij.polySymbols.patterns.ComplexPatternOptions
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.impl.*
import com.intellij.polySymbols.query.*
import com.intellij.polySymbols.utils.namespace
import com.intellij.polySymbols.webTypes.WebTypesJsonOrigin
import com.intellij.polySymbols.webTypes.json.*

internal fun NamePatternRoot.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): PolySymbolPattern =
  when (val value = value) {
    is String -> RegExpPattern(value)
    is NamePatternBase -> value.wrap(defaultDisplayName, origin)
    else -> throw IllegalArgumentException(value::class.java.name)
  }

internal fun NamePatternBase.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): PolySymbolPattern =
  when (this) {
    is NamePatternRegex -> RegExpPattern(regex, caseSensitive == true)
    is NamePatternDefault -> ComplexPattern(WebTypesComplexPatternConfigProvider(this, defaultDisplayName, origin))
    else -> throw IllegalArgumentException(this::class.java.name)
  }

@Suppress("UNCHECKED_CAST")
internal fun NamePatternTemplate.wrap(defaultDisplayName: String?, origin: WebTypesJsonOrigin): PolySymbolPattern =
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

private class SequencePatternPatternsProvider(
  private val list: List<NamePatternTemplate>,
  private val origin: WebTypesJsonOrigin,
) : () -> List<PolySymbolPattern> {
  override fun invoke(): List<PolySymbolPattern> =
    list.map { it.wrap(null, origin) }
}

private class WebTypesComplexPatternConfigProvider(
  private val pattern: NamePatternDefault,
  private val defaultDisplayName: String?,
  private val origin: WebTypesJsonOrigin,
) : ComplexPatternConfigProvider {

  override fun getPatterns(): List<PolySymbolPattern> =
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

  override fun getOptions(
    queryExecutor: PolySymbolQueryExecutor,
    stack: PolySymbolQueryStack,
  ): ComplexPatternOptions {
    val queryParams = PolySymbolNameMatchQueryParams.create(queryExecutor) {
      exclude(PolySymbolModifier.ABSTRACT)
    }
    val delegate = pattern.delegate?.resolve(stack, queryParams.queryExecutor)?.firstOrNull()

    // Allow delegate pattern to override settings
    val apiStatus = delegate?.apiStatus?.takeIf { it.isDeprecatedOrObsolete() }
                    ?: pattern.toApiStatus(origin)?.takeIf { it.isDeprecatedOrObsolete() }
    val delegateRequired = delegate?.modifiers?.let { modifiers ->
      when {
        modifiers.contains(PolySymbolModifier.REQUIRED) -> true
        modifiers.contains(PolySymbolModifier.OPTIONAL) -> false
        else -> null
      }
    }
    val isRequired = (delegateRequired ?: pattern.required) != false
    val priority = delegate?.priority ?: pattern.priority?.wrap()
    val repeats = pattern.repeat == true
    val unique = pattern.unique != false

    val symbolsResolver = createSymbolsResolver(delegate)
    return ComplexPatternOptions(delegate, apiStatus, isRequired, priority, repeats, unique, symbolsResolver)
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

  private class PatternDelegateSymbolsResolver(override val delegate: PolySymbol) : com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver {
    override fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
      setOf(delegate.qualifiedKind)

    override fun codeCompletion(
      name: String,
      position: Int,
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
    ): List<PolySymbolCodeCompletionItem> =
      (delegate as? PolySymbolWithPattern)
        ?.pattern
        ?.complete(delegate, stack,
                   this, CompletionParameters(name, queryExecutor, position), 0, name.length)
        ?.items
        ?.applyIcons(delegate)
      ?: emptyList()

    override fun listSymbols(
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
      expandPatterns: Boolean,
    ): List<PolySymbol> =
      (delegate as? PolySymbolWithPattern)
        ?.pattern
        ?.list(delegate, stack, this, ListParameters(queryExecutor, expandPatterns))
        ?.flatMap { listResult ->
          if (listResult.segments.size == 1
              && listResult.segments[0].canUnwrapSymbols()) {
            listResult.segments[0].symbols
          }
          else {
            val lastContribution = stack.peek() as PolySymbol
            listOf(PolySymbolMatch.create(listResult.name, listResult.segments,
                                          PolySymbolQualifiedKind[lastContribution.namespace, SPECIAL_MATCHED_CONTRIB],
                                          lastContribution.origin))
          }
        }
      ?: emptyList()

    override fun matchName(name: String, stack: PolySymbolQueryStack, queryExecutor: PolySymbolQueryExecutor): List<PolySymbol> =
      (delegate as? PolySymbolWithPattern)
        ?.pattern
        ?.match(delegate, stack, null,
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
            val lastContribution = stack.peek() as PolySymbol
            sequenceOf(PolySymbolMatch.create(name, matchResult.segments,
                                              PolySymbolQualifiedKind[lastContribution.namespace, SPECIAL_MATCHED_CONTRIB],
                                              lastContribution.origin))
          }
        }
      ?: emptyList()

  }

  private class PatternSymbolsResolver(val items: ListReference) : com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver {
    override fun getSymbolKinds(context: PolySymbol?): Set<PolySymbolQualifiedKind> =
      items.asSequence().mapNotNull { it.getSymbolKind(context) }.toSet()

    override val delegate: PolySymbol?
      get() = null

    override fun codeCompletion(
      name: String,
      position: Int,
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
    ): List<PolySymbolCodeCompletionItem> =
      items.flatMap { it.codeCompletion(name, stack, queryExecutor, position) }

    override fun listSymbols(
      stack: PolySymbolQueryStack,
      queryExecutor: PolySymbolQueryExecutor,
      expandPatterns: Boolean,
    ): List<PolySymbol> =
      items.flatMap { it.list(stack, queryExecutor, expandPatterns) }

    override fun matchName(name: String, stack: PolySymbolQueryStack, queryExecutor: PolySymbolQueryExecutor): List<PolySymbol> =
      items.flatMap { it.resolve(name, stack, queryExecutor) }

  }
}
