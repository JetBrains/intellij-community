// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns.impl

import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.patterns.AlternativesBuilder
import com.intellij.polySymbols.patterns.GroupPatternBuilder
import com.intellij.polySymbols.patterns.PolySymbolPattern
import com.intellij.polySymbols.patterns.PolySymbolPatternBuilder
import com.intellij.polySymbols.patterns.RepeatingGroupPatternBuilder
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack

internal open class PolySymbolPatternBuilderImpl : PolySymbolPatternBuilder {

  internal val patterns: MutableList<PolySymbolPattern> = mutableListOf()

  override fun literal(text: String) {
    patterns += StaticPattern(text)
  }

  override fun regex(pattern: String, caseSensitive: Boolean) {
    patterns += RegExpPattern(pattern, caseSensitive)
  }

  override fun symbolReference(label: String?) {
    patterns += SymbolReferencePattern(label)
  }

  override fun completionPopup() {
    patterns += CompletionAutoPopupPattern(false)
  }

  override fun completionPopupWithPrefixKept() {
    patterns += CompletionAutoPopupPattern(true)
  }

  override fun symbolReference(vararg path: PolySymbolQualifiedName) {
    patterns += SingleSymbolReferencePattern(path.toList())
  }

  override fun symbolReference(path: List<PolySymbolQualifiedName>) {
    patterns += SingleSymbolReferencePattern(path.toList())
  }

  override fun sequence(body: PolySymbolPatternBuilder.() -> Unit) {
    patterns += PolySymbolPatternBuilderImpl().apply(body).buildSingle()
  }

  override fun oneOf(body: AlternativesBuilder.() -> Unit) {
    val branches = AlternativesBuilderImpl().apply(body).buildBranches()
    val complexOptions = ComplexPatternOptions()
    val complexPatterns = branches.toList()
    patterns += ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<PolySymbolPattern> = complexPatterns
      override fun getOptions(queryExecutor: PolySymbolQueryExecutor, stack: PolySymbolQueryStack): ComplexPatternOptions = complexOptions
      override val isStaticAndRequired: Boolean get() = false
    })
  }

  override fun group(body: GroupPatternBuilder.() -> Unit) {
    patterns += GroupPatternBuilderImpl(required = true).apply(body).buildGroup()
  }

  override fun optional(body: GroupPatternBuilder.() -> Unit) {
    patterns += GroupPatternBuilderImpl(required = false).apply(body).buildGroup()
  }

  override fun repeating(body: RepeatingGroupPatternBuilder.() -> Unit) {
    patterns += RepeatingGroupPatternBuilderImpl(required = true).apply(body).buildGroup()
  }

  override fun optionalRepeating(body: RepeatingGroupPatternBuilder.() -> Unit) {
    patterns += RepeatingGroupPatternBuilderImpl(required = false).apply(body).buildGroup()
  }

  internal fun buildSingle(): PolySymbolPattern {
    check(patterns.isNotEmpty()) { "Pattern body must produce at least one pattern" }
    return if (patterns.size == 1) patterns[0]
    else SequencePattern(*patterns.toTypedArray())
  }
}
