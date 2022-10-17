// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes.impl

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.patterns.ComplexPatternOptions
import com.intellij.webSymbols.patterns.WebSymbolsPattern
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.registry.WebSymbolMatch
import com.intellij.webSymbols.registry.WebSymbolsNameMatchQueryParams
import com.intellij.webSymbols.registry.WebSymbolsRegistry
import com.intellij.webSymbols.webTypes.json.*

internal fun NamePatternRoot.wrap(defaultDisplayName: String?): WebSymbolsPattern =
  when (val value = value) {
    is String -> RegExpPattern(value)
    is NamePatternBase -> value.wrap(defaultDisplayName)
    else -> throw IllegalArgumentException(value::class.java.name)
  }

internal fun NamePatternBase.wrap(defaultDisplayName: String?): WebSymbolsPattern =
  when (this) {
    is NamePatternRegex -> RegExpPattern(regex, caseSensitive == true)
    is NamePatternDefault -> ComplexPattern(WebTypesComplexPatternConfigProvider(this, defaultDisplayName))
    else -> throw IllegalArgumentException(this::class.java.name)
  }

@Suppress("UNCHECKED_CAST")
internal fun NamePatternTemplate.wrap(defaultDisplayName: String?): WebSymbolsPattern =
  when (val value = value) {
    is String -> when {
      value == "#item" -> ItemPattern(defaultDisplayName)
      value.startsWith("#item:") -> ItemPattern(value.substring(6))
      value == "#..." -> CompletionAutoPopupPattern(false)
      value == "$..." -> CompletionAutoPopupPattern(true)
      else -> StaticPattern(value)
    }
    is NamePatternBase -> value.wrap(defaultDisplayName)
    is List<*> -> SequencePattern(SequencePatternPatternsProvider(value as List<NamePatternTemplate>))
    else -> throw IllegalArgumentException(value::class.java.name)
  }

private class SequencePatternPatternsProvider(private val list: List<NamePatternTemplate>) : () -> List<WebSymbolsPattern> {
  override fun invoke(): List<WebSymbolsPattern> =
    list.map { it.wrap(null) }
}

private class WebTypesComplexPatternConfigProvider(private val pattern: NamePatternDefault,
                                                   private val defaultDisplayName: String?) : ComplexPatternConfigProvider {

  override fun getPatterns(): List<WebSymbolsPattern> =
    pattern.or.asSequence()
      .map { it.wrap(defaultDisplayName) }
      .let {
        val template = pattern.template
        if (template.isEmpty()) {
          it
        }
        else {
          it.plus(SequencePattern(SequencePatternPatternsProvider(template)))
        }
      }
      .toList()
      .ifEmpty { listOf(SequencePattern(ItemPattern(defaultDisplayName))) }


  override val isStaticAndRequired: Boolean
    get() = pattern.delegate == null && pattern.items == null && pattern.required != false

  override fun getOptions(params: MatchParameters,
                          contextStack: Stack<WebSymbolsContainer>): ComplexPatternOptions {
    val queryParams = WebSymbolsNameMatchQueryParams(params.registry, true, false)
    val delegate = pattern.delegate?.resolve(null, contextStack, queryParams.registry)?.firstOrNull()

    // Allow delegate pattern to override settings
    val isDeprecated = delegate?.deprecated ?: pattern.deprecated
    val isRequired = (delegate?.required ?: pattern.required) != false
    val priority = delegate?.priority ?: pattern.priority?.wrap()
    val proximity = delegate?.proximity ?: pattern.proximity
    val repeats = pattern.repeat == true
    val unique = pattern.unique != false

    val itemsProvider = createItemsProvider(delegate)
    return ComplexPatternOptions(delegate, isDeprecated, isRequired, priority, proximity, repeats, unique, itemsProvider)
  }

  private fun createItemsProvider(delegate: WebSymbol?) =
    if (pattern.delegate != null) {
      if (delegate != null) {
        PatternDelegateItemsProvider(delegate)
      }
      else null
    }
    else
      pattern.items
        ?.takeIf { it.isNotEmpty() }
        ?.let {
          PatternItemsProvider(it)
        }

  private class PatternDelegateItemsProvider(override val delegate: WebSymbol) : com.intellij.webSymbols.patterns.WebSymbolsPatternItemsProvider {
    override fun getSymbolKinds(context: WebSymbol?): Set<WebSymbolQualifiedKind> =
      setOf(WebSymbolQualifiedKind(delegate.namespace, delegate.kind))

    override fun codeCompletion(name: String,
                                position: Int,
                                contextStack: Stack<WebSymbolsContainer>,
                                registry: WebSymbolsRegistry): List<WebSymbolCodeCompletionItem> =
      delegate.pattern
        ?.getCompletionResults(delegate, contextStack,
                               this, CompletionParameters(name, registry, position), 0, name.length)
        ?.items
        ?.applyIcons(delegate)
      ?: emptyList()

    override fun matchName(name: String, contextStack: Stack<WebSymbolsContainer>, registry: WebSymbolsRegistry): List<WebSymbol> =
      delegate.pattern
        ?.match(delegate, contextStack, null,
                MatchParameters(name, registry), 0, name.length)
        ?.asSequence()
        ?.flatMap { matchResult ->
          if (matchResult.start == matchResult.end) {
            emptySequence()
          }
          else if (matchResult.segments.size == 1
                   && matchResult.segments[0].canUnwrapSymbols()) {
            matchResult.segments[0].symbols.asSequence()
          }
          else {
            val lastContribution = contextStack.peek() as WebSymbol
            sequenceOf(WebSymbolMatch.create(name, matchResult.segments,
                                             lastContribution.namespace, SPECIAL_MATCHED_CONTRIB,
                                             lastContribution.origin))
          }
        }
        ?.toList()
      ?: emptyList()

  }

  private class PatternItemsProvider(val items: ListReference) : com.intellij.webSymbols.patterns.WebSymbolsPatternItemsProvider {
    override fun getSymbolKinds(context: WebSymbol?): Set<WebSymbolQualifiedKind> =
      items.asSequence().mapNotNull { it.getSymbolKind(context) }.toSet()

    override val delegate: WebSymbol?
      get() = null

    override fun codeCompletion(name: String,
                                position: Int,
                                contextStack: Stack<WebSymbolsContainer>,
                                registry: WebSymbolsRegistry): List<WebSymbolCodeCompletionItem> =
      items.flatMap { it.codeCompletion(name, contextStack, registry, position) }

    override fun matchName(name: String, contextStack: Stack<WebSymbolsContainer>, registry: WebSymbolsRegistry): List<WebSymbol> =
      items.asSequence()
        .flatMap { it.resolve(name, contextStack, registry) }
        .flatMap {
          if (it is WebSymbolMatch
              && it.nameSegments.size == 1
              && it.nameSegments[0].canUnwrapSymbols())
            it.nameSegments[0].symbols
          else listOf(it)
        }
        .toList()

  }
}
