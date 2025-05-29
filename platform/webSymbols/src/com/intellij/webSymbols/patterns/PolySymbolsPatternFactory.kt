// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.PolySymbolQualifiedName
import com.intellij.webSymbols.PolySymbolsScope
import com.intellij.webSymbols.patterns.impl.*
import com.intellij.webSymbols.query.PolySymbolsQueryExecutor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object PolySymbolsPatternFactory {

  fun createComplexPattern(optionsProvider: (queryExecutor: PolySymbolsQueryExecutor, contextStack: Stack<PolySymbolsScope>) -> ComplexPatternOptions,
                           isStaticAndRequiredProvider: () -> Boolean,
                           patternsProvider: () -> List<PolySymbolsPattern>): PolySymbolsPattern =
    ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<PolySymbolsPattern> =
        patternsProvider()

      override fun getOptions(queryExecutor: PolySymbolsQueryExecutor, scopeStack: Stack<PolySymbolsScope>): ComplexPatternOptions =
        optionsProvider(queryExecutor, scopeStack)

      override val isStaticAndRequired: Boolean
        get() = isStaticAndRequiredProvider()

    })

  fun createComplexPattern(options: ComplexPatternOptions,
                           isStaticAndRequired: Boolean,
                           vararg patterns: PolySymbolsPattern): PolySymbolsPattern =

    ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<PolySymbolsPattern> =
        patterns.toList()

      override fun getOptions(queryExecutor: PolySymbolsQueryExecutor, scopeStack: Stack<PolySymbolsScope>): ComplexPatternOptions =
        options

      override val isStaticAndRequired: Boolean
        get() = isStaticAndRequired

    })

  fun createPatternSequence(vararg patterns: PolySymbolsPattern): PolySymbolsPattern =
    SequencePattern { patterns.toList() }

  fun createPatternSequence(patternsProvider: () -> List<PolySymbolsPattern>): PolySymbolsPattern =
    SequencePattern(patternsProvider)

  fun createSymbolReferencePlaceholder(displayName: String? = null): PolySymbolsPattern =
    SymbolReferencePattern(displayName)

  fun createStringMatch(content: String): PolySymbolsPattern =
    StaticPattern(content)

  fun createRegExMatch(regex: String, caseSensitive: Boolean = false): PolySymbolsPattern =
    RegExpPattern(regex, caseSensitive)

  fun createCompletionAutoPopup(isSticky: Boolean): PolySymbolsPattern =
    CompletionAutoPopupPattern(isSticky)

  fun createSingleSymbolReferencePattern(path: List<PolySymbolQualifiedName>): PolySymbolsPattern =
    SingleSymbolReferencePattern(path.toList())

}