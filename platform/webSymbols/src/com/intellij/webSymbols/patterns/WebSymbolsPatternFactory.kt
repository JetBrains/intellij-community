// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.patterns

import com.intellij.util.containers.Stack
import com.intellij.webSymbols.WebSymbolsScope
import com.intellij.webSymbols.query.WebSymbolsQueryExecutor
import com.intellij.webSymbols.patterns.impl.*

object WebSymbolsPatternFactory {

  fun createComplexPattern(optionsProvider: (queryExecutor: WebSymbolsQueryExecutor, contextStack: Stack<WebSymbolsScope>) -> ComplexPatternOptions,
                           isStaticAndRequiredProvider: () -> Boolean,
                           patternsProvider: () -> List<WebSymbolsPattern>): WebSymbolsPattern =
    ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<WebSymbolsPattern> =
        patternsProvider()

      override fun getOptions(params: MatchParameters, scopeStack: Stack<WebSymbolsScope>): ComplexPatternOptions =
        optionsProvider(params.queryExecutor, scopeStack)

      override val isStaticAndRequired: Boolean
        get() = isStaticAndRequiredProvider()

    })

  fun createComplexPattern(options: ComplexPatternOptions,
                           isStaticAndRequired: Boolean,
                           vararg patterns: WebSymbolsPattern): WebSymbolsPattern =

    ComplexPattern(object : ComplexPatternConfigProvider {
      override fun getPatterns(): List<WebSymbolsPattern> =
        patterns.toList()

      override fun getOptions(params: MatchParameters, scopeStack: Stack<WebSymbolsScope>): ComplexPatternOptions =
        options

      override val isStaticAndRequired: Boolean
        get() = isStaticAndRequired

    })

  fun createPatternSequence(vararg patterns: WebSymbolsPattern): WebSymbolsPattern =
    SequencePattern { patterns.toList() }

  fun createPatternSequence(patternsProvider: () -> List<WebSymbolsPattern>): WebSymbolsPattern =
    SequencePattern(patternsProvider)

  fun createSymbolReferencePlaceholder(displayName: String? = null): WebSymbolsPattern =
    SymbolReferencePattern(displayName)

  fun createStringMatch(content: String): WebSymbolsPattern =
    StaticPattern(content)

  fun createRegExMatch(regex: String, caseSensitive: Boolean = false): WebSymbolsPattern =
    RegExpPattern(regex, caseSensitive)

  fun createCompletionAutoPopup(isSticky: Boolean): WebSymbolsPattern =
    CompletionAutoPopupPattern(isSticky)

}