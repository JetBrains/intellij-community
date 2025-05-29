// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.webSymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiElement
import com.intellij.webSymbols.*
import com.intellij.webSymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.webSymbols.context.PolyContext
import com.intellij.webSymbols.context.PolyContext.Companion.KIND_FRAMEWORK

/**
 * To create a query executor use [PolySymbolsQueryExecutorFactory].
 * The query executor will be configured by all the registered [PolySymbolsQueryConfigurator]'s
 * based on the provided source code location. Configurators will provide initial Web Symbol scopes,
 * rules for calculating Web Symbols context and rules for symbol names conversion.
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface PolySymbolsQueryExecutor : ModificationTracker {

  val location: PsiElement?

  val context: PolyContext

  val framework: FrameworkId? get() = context[KIND_FRAMEWORK]

  @get:JvmName("allowResolve")
  val allowResolve: Boolean

  val namesProvider: PolySymbolNamesProvider

  val resultsCustomizer: PolySymbolsQueryResultsCustomizer

  var keepUnresolvedTopLevelReferences: Boolean

  fun createPointer(): Pointer<PolySymbolsQueryExecutor>

  fun runNameMatchQuery(namespace: SymbolNamespace,
                        kind: SymbolKind,
                        name: String,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbol> =
    runNameMatchQuery(listOf(PolySymbolQualifiedName(namespace, kind, name)), virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runNameMatchQuery(qualifiedName: PolySymbolQualifiedName,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbol> =
    runNameMatchQuery(listOf(qualifiedName), virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runNameMatchQuery(path: List<PolySymbolQualifiedName>,
                        virtualSymbols: Boolean = true,
                        abstractSymbols: Boolean = false,
                        strictScope: Boolean = false,
                        additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbol>

  fun runListSymbolsQuery(qualifiedKind: PolySymbolQualifiedKind,
                          expandPatterns: Boolean,
                          virtualSymbols: Boolean = true,
                          abstractSymbols: Boolean = false,
                          strictScope: Boolean = false,
                          additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbol> =
    runListSymbolsQuery(emptyList(), qualifiedKind, expandPatterns, virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runListSymbolsQuery(path: List<PolySymbolQualifiedName>,
                          qualifiedKind: PolySymbolQualifiedKind,
                          expandPatterns: Boolean,
                          virtualSymbols: Boolean = true,
                          abstractSymbols: Boolean = false,
                          strictScope: Boolean = false,
                          additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbol>

  fun runCodeCompletionQuery(namespace: SymbolNamespace,
                             kind: SymbolKind,
                             name: String,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbolCodeCompletionItem> =
    runCodeCompletionQuery(listOf(PolySymbolQualifiedName(namespace, kind, name)), position, virtualSymbols, additionalScope)

  fun runCodeCompletionQuery(qualifiedKind: PolySymbolQualifiedKind,
                             name: String,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbolCodeCompletionItem> =
    runCodeCompletionQuery(listOf(qualifiedKind.withName(name)), position, virtualSymbols, additionalScope)

  fun runCodeCompletionQuery(path: List<PolySymbolQualifiedName>,
                             /** Position to complete at in the last segment of the path **/
                             position: Int,
                             virtualSymbols: Boolean = true,
                             additionalScope: List<PolySymbolsScope> = emptyList()): List<PolySymbolCodeCompletionItem>

  fun withNameConversionRules(rules: List<PolySymbolNameConversionRules>): PolySymbolsQueryExecutor

  fun hasExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind, scope: List<PolySymbolsScope> = emptyList()): Boolean

}