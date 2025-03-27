// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.WebSymbolCodeCompletionItem
import com.intellij.webSymbols.context.WebSymbolsContext
import com.intellij.webSymbols.context.WebSymbolsContext.Companion.KIND_FRAMEWORK

/**
 * To create a query executor use [WebSymbolsQueryExecutorFactory].
 * The query executor will be configured by all the registered [WebSymbolsQueryConfigurator]'s
 * based on the provided source code location. Configurators will provide initial Web Symbol scopes,
 * rules for calculating Web Symbols context and rules for symbol names conversion.
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface WebSymbolsQueryExecutor : ModificationTracker {

  val location: PsiElement?

  val context: WebSymbolsContext

  val framework: FrameworkId? get() = context[KIND_FRAMEWORK]

  @get:JvmName("allowResolve")
  val allowResolve: Boolean

  val namesProvider: WebSymbolNamesProvider

  val resultsCustomizer: WebSymbolsQueryResultsCustomizer

  var keepUnresolvedTopLevelReferences: Boolean

  fun createPointer(): Pointer<WebSymbolsQueryExecutor>

  fun runNameMatchQuery(namespace: SymbolNamespace,
                        kind: SymbolKind,
                        name: String,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbol> =
    runNameMatchQuery(listOf(WebSymbolQualifiedName(namespace, kind, name)), virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runNameMatchQuery(qualifiedName: WebSymbolQualifiedName,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbol> =
    runNameMatchQuery(listOf(qualifiedName), virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runNameMatchQuery(path: List<WebSymbolQualifiedName>,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbol>

  fun runListSymbolsQuery(qualifiedKind: WebSymbolQualifiedKind,
                          expandPatterns: Boolean,
                          virtualSymbols: Boolean = true,
                          abstractSymbols: Boolean = false,
                          strictScope: Boolean = false,
                          additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbol> =
    runListSymbolsQuery(emptyList(), qualifiedKind, expandPatterns, virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runListSymbolsQuery(path: List<WebSymbolQualifiedName>,
                          qualifiedKind: WebSymbolQualifiedKind,
                          expandPatterns: Boolean,
                          virtualSymbols: Boolean = true,
                          abstractSymbols: Boolean = false,
                          strictScope: Boolean = false,
                          additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbol>

  fun runCodeCompletionQuery(namespace: SymbolNamespace,
                             kind: SymbolKind,
                             name: String,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbolCodeCompletionItem> =
    runCodeCompletionQuery(listOf(WebSymbolQualifiedName(namespace, kind, name)), position, virtualSymbols, additionalScope)

  fun runCodeCompletionQuery(qualifiedKind: WebSymbolQualifiedKind,
                             name: String,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbolCodeCompletionItem> =
    runCodeCompletionQuery(listOf(qualifiedKind.withName(name)), position, virtualSymbols, additionalScope)

  fun runCodeCompletionQuery(path: List<WebSymbolQualifiedName>,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             additionalScope: List<WebSymbolsScope> = emptyList()): List<WebSymbolCodeCompletionItem>

  fun withNameConversionRules(rules: List<WebSymbolNameConversionRules>): WebSymbolsQueryExecutor

  fun hasExclusiveScopeFor(qualifiedKind: WebSymbolQualifiedKind, scope: List<WebSymbolsScope> = emptyList()): Boolean

}