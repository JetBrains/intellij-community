// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.patterns

import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.patterns.impl.*
import com.intellij.polySymbols.query.PolySymbolQueryExecutor
import com.intellij.polySymbols.query.PolySymbolQueryStack
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PolySymbolPatternFactory {

  fun createComplexPattern(
    optionsProvider: (queryExecutor: PolySymbolQueryExecutor, stack: PolySymbolQueryStack) -> ComplexPatternOptions,
    isStaticAndRequiredProvider: () -> Boolean,
    patternsProvider: () -> List<PolySymbolPattern>,
  ): PolySymbolPattern =
    ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<PolySymbolPattern> =
        patternsProvider()

      override fun getOptions(queryExecutor: PolySymbolQueryExecutor, stack: PolySymbolQueryStack): ComplexPatternOptions =
        optionsProvider(queryExecutor, stack)

      override val isStaticAndRequired: Boolean
        get() = isStaticAndRequiredProvider()

    })

  fun createComplexPattern(
    options: ComplexPatternOptions,
    isStaticAndRequired: Boolean,
    vararg patterns: PolySymbolPattern,
  ): PolySymbolPattern =

    ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<PolySymbolPattern> =
        patterns.toList()

      override fun getOptions(queryExecutor: PolySymbolQueryExecutor, stack: PolySymbolQueryStack): ComplexPatternOptions =
        options

      override val isStaticAndRequired: Boolean
        get() = isStaticAndRequired

    })

  fun createPatternSequence(vararg patterns: PolySymbolPattern): PolySymbolPattern =
    SequencePattern { patterns.toList() }

  fun createPatternSequence(patternsProvider: () -> List<PolySymbolPattern>): PolySymbolPattern =
    SequencePattern(patternsProvider)

  fun createSymbolReferencePlaceholder(displayName: String? = null): PolySymbolPattern =
    SymbolReferencePattern(displayName)

  fun createStringMatch(content: String): PolySymbolPattern =
    StaticPattern(content)

  fun createRegExMatch(regex: String, caseSensitive: Boolean = false): PolySymbolPattern =
    RegExpPattern(regex, caseSensitive)

  fun createCompletionAutoPopup(isSticky: Boolean): PolySymbolPattern =
    CompletionAutoPopupPattern(isSticky)

  fun createSingleSymbolReferencePattern(path: List<PolySymbolQualifiedName>): PolySymbolPattern =
    SingleSymbolReferencePattern(path.toList())

}