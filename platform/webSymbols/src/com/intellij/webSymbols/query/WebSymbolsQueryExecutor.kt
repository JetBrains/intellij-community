// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.text.StringUtil
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK

/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 * DEPRECATION -> @JvmDefault
 **/
@Suppress("INAPPLICABLE_JVM_NAME", "DEPRECATION")
interface WebSymbolsQueryExecutor : ModificationTracker {

  val context: WebSymbolsContext

  val framework: FrameworkId? get() = context[KIND_FRAMEWORK]

  @get:JvmName("allowResolve")
  val allowResolve: Boolean

  val namesProvider: WebSymbolNamesProvider

  val resultsCustomizer: WebSymbolsQueryResultsCustomizer

  fun createPointer(): Pointer<WebSymbolsQueryExecutor>

  fun runNameMatchQuery(path: String,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        scope: List<WebSymbolsScope> = emptyList()): List<WebSymbol> =
    runNameMatchQuery(StringUtil.split(path, "/", true, true),
                      virtualSymbols, abstractSymbols, strictScope, scope)

  fun runNameMatchQuery(path: List<String>,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        scope: List<WebSymbolsScope> = emptyList()): List<WebSymbol>

  fun runCodeCompletionQuery(path: String,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             scope: List<WebSymbolsScope> = emptyList()): List<WebSymbolCodeCompletionItem> =
    runCodeCompletionQuery(StringUtil.split(path, "/", true, true), position, virtualSymbols, scope)

  fun runCodeCompletionQuery(path: List<String>,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             scope: List<WebSymbolsScope> = emptyList()): List<WebSymbolCodeCompletionItem>

  fun withNameConversionRules(rules: List<WebSymbolNameConversionRules>): WebSymbolsQueryExecutor
}