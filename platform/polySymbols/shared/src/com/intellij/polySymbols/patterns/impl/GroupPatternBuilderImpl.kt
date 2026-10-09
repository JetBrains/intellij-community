// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolApiStatus
import com.intellij.polySymbols.patterns.AlternativesBuilder
import com.intellij.polySymbols.patterns.GroupPatternBuilder
import com.intellij.polySymbols.patterns.MatchPropertyOverridesBuilder
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternSymbolsResolver
import com.intellij.polySymbols.patterns.SymbolsBuilder
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import com.intellij.polySymbols.query.PolySymbolScope

internal open class GroupPatternBuilderImpl(
  private val required: Boolean,
) : PolySymbolPatternBuilderImpl(), GroupPatternBuilder {
  private var priorityValue: PolySymbol.Priority? = null
  private var apiStatusValue: PolySymbolApiStatus? = null
  private var symbolsResolverValue: PolySymbolPatternSymbolsResolver? = null
  private var matchPropertyOverrides: MatchPropertyOverridesBuilderImpl? = null
  private val additionalScopes: MutableList<PolySymbolScope> = mutableListOf()
  protected open val repeats: Boolean get() = false
  protected open val unique: Boolean get() = false
  private var symbolsBuilder: SymbolsBuilderImpl? = null
  private val alternatives: MutableList<PolySymbolPattern> = mutableListOf()

  override fun priority(value: PolySymbol.Priority?) {
    priorityValue = value
  }

  override fun apiStatus(value: PolySymbolApiStatus?) {
    apiStatusValue = value
  }

  override fun symbolsResolver(value: PolySymbolPatternSymbolsResolver?) {
    symbolsResolverValue = value
  }

  override fun overrideMatchProperties(body: MatchPropertyOverridesBuilder.() -> Unit) {
    val builder = matchPropertyOverrides ?: MatchPropertyOverridesBuilderImpl().also { matchPropertyOverrides = it }
    builder.body()
  }

  override fun additionalScope(vararg scopes: PolySymbolScope) {
    additionalScopes += scopes
  }

  override fun additionalScope(scopes: Collection<PolySymbolScope>) {
    additionalScopes += scopes
  }

  override fun oneOf(body: AlternativesBuilder.() -> Unit) {
    alternatives += AlternativesBuilderImpl().apply(body).buildBranches()
  }

  override fun symbols(body: SymbolsBuilder.() -> Unit) {
    val builder = symbolsBuilder ?: SymbolsBuilderImpl().also { symbolsBuilder = it }
    builder.body()
  }

  internal fun buildGroup(): PolySymbolPattern {
    check(symbolsBuilder == null || symbolsResolverValue == null) {
      "Group has both a symbols { } block and a symbolsResolver — pick one"
    }

    val resolver = symbolsResolverValue ?: symbolsBuilder?.buildResolver()

    val content: MutableList<PolySymbolPattern> = mutableListOf()
    content += alternatives
    if (patterns.isNotEmpty()) {
      content += if (patterns.size == 1) patterns[0]
      else SequencePattern(*patterns.toTypedArray())
    }
    check(content.isNotEmpty()) { "Group body must produce at least one pattern" }

    val options = ComplexPatternOptions(
      additionalScope = additionalScopes.toList(),
      apiStatus = apiStatusValue,
      isRequired = required,
      priority = priorityValue,
      repeats = repeats,
      unique = repeats && unique,
      symbolsResolver = resolver,
      additionalLastSegmentSymbol = matchPropertyOverrides?.build(),
    )
    val complexPatterns = content.toList()
    return ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<PolySymbolPattern> = complexPatterns
      override fun getOptions(queryExecutor: PolySymbolQueryExecutor, stack: PolySymbolQueryStack): ComplexPatternOptions = options
      override val isStaticAndRequired: Boolean get() = false
    })
  }
}
