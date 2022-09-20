// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.webTypes

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.*
import com.intellij.webSymbols.patterns.*
import com.intellij.webSymbols.webTypes.json.*
import com.intellij.webSymbols.webTypes.json.codeCompletion
import com.intellij.webSymbols.webTypes.json.getSymbolType
import com.intellij.webSymbols.webTypes.json.resolve

fun NamePatternRoot.wrap(defaultDisplayName: String?): WebSymbolsPattern =
  when (val value = value) {
    is String -> RegExpPattern(value)
    is NamePatternBase -> value.wrap(defaultDisplayName)
    else -> throw IllegalArgumentException(value::class.java.name)
  }

fun NamePatternBase.wrap(defaultDisplayName: String?): WebSymbolsPattern =
  when (this) {
    is NamePatternRegex -> RegExpPattern(regex, caseSensitive == true)
    is NamePatternDefault -> ComplexPattern(WebTypesComplexPatternConfigProvider(this),
                                                                                                     defaultDisplayName)
    else -> throw IllegalArgumentException(this::class.java.name)
  }

@Suppress("UNCHECKED_CAST")
fun NamePatternTemplate.wrap(defaultDisplayName: String?): WebSymbolsPattern =
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

private class WebTypesComplexPatternConfigProvider(private val pattern: NamePatternDefault) : ComplexPattern.ComplexPatternConfigProvider {

  override fun getPatterns(defaultDisplayName: String?): List<WebSymbolsPattern> =
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


  override fun isStaticAndRequired(): Boolean =
    pattern.delegate == null && pattern.items == null && pattern.required != false

  override fun getOptions(params: WebSymbolsPattern.MatchParameters,
                          contextStack: Stack<WebSymbolsContainer>): ComplexPattern.ComplexPatternOptions {
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
    return ComplexPattern.ComplexPatternOptions(delegate, isDeprecated, isRequired, priority, proximity, repeats, unique, itemsProvider)
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

  private class PatternDelegateItemsProvider(override val delegate: WebSymbol) : WebSymbolsPattern.ItemsProvider {
    override fun getSymbolTypes(context: WebSymbol?): Set<WebSymbol.SymbolType> =
      setOf(WebSymbol.SymbolType(delegate.namespace, delegate.kind))

    override fun codeCompletion(name: String,
                                position: Int,
                                contextStack: Stack<WebSymbolsContainer>,
                                registry: WebSymbolsRegistry): List<WebSymbolCodeCompletionItem> =
      delegate.pattern
        ?.getCompletionResults(delegate, contextStack,
                               this, WebSymbolsPattern.CompletionParameters(name, registry, position), 0, name.length)
        ?.items
        ?.applyIcons(delegate)
      ?: emptyList()

    override fun matchName(name: String, contextStack: Stack<WebSymbolsContainer>, registry: WebSymbolsRegistry): List<WebSymbol> =
      delegate.pattern
        ?.match(delegate, contextStack, null,
                WebSymbolsPattern.MatchParameters(name, registry), 0, name.length)
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
                                             lastContribution.namespace, WebSymbolsPattern.SPECIAL_MATCHED_CONTRIB,
                                             lastContribution.origin))
          }
        }
        ?.toList()
      ?: emptyList()

  }

  private class PatternItemsProvider(val items: ListReference) : WebSymbolsPattern.ItemsProvider {
    override fun getSymbolTypes(context: WebSymbol?): Set<WebSymbol.SymbolType> =
      items.asSequence().mapNotNull { it.getSymbolType(context) }.toSet()

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
