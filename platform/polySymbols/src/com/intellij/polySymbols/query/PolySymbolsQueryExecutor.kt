// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.*
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.polySymbols.context.PolyContext.Companion.KIND_FRAMEWORK
import com.intellij.psi.PsiElement

/**
 * To create a query executor use [PolySymbolsQueryExecutorFactory].
 * The query executor will be configured by all the registered [PolySymbolsQueryConfigurator]'s
 * based on the provided source code location. Configurators will provide initial Poly Symbol scopes,
 * rules for calculating PolyContext and rules for symbol names conversion.
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

  fun runNameMatchQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
    virtualSymbols: Boolean = true,
    abstractSymbols: Boolean = false,
    strictScope: Boolean = false,
    additionalScope: List<PolySymbolsScope> = emptyList(),
  ): List<PolySymbol> =
    runNameMatchQuery(listOf(qualifiedKind.withName(name)), virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runNameMatchQuery(
    path: List<PolySymbolQualifiedName>,
    virtualSymbols: Boolean = true,
    abstractSymbols: Boolean = false,
    strictScope: Boolean = false,
    additionalScope: List<PolySymbolsScope> = emptyList(),
  ): List<PolySymbol>

  fun runListSymbolsQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
    virtualSymbols: Boolean = true,
    abstractSymbols: Boolean = false,
    strictScope: Boolean = false,
    additionalScope: List<PolySymbolsScope> = emptyList(),
  ): List<PolySymbol> =
    runListSymbolsQuery(emptyList(), qualifiedKind, expandPatterns, virtualSymbols, abstractSymbols, strictScope, additionalScope)

  fun runListSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
    virtualSymbols: Boolean = true,
    abstractSymbols: Boolean = false,
    strictScope: Boolean = false,
    additionalScope: List<PolySymbolsScope> = emptyList(),
  ): List<PolySymbol>

  fun runCodeCompletionQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    virtualSymbols: Boolean = true,
    additionalScope: List<PolySymbolsScope> = emptyList(),
  ): List<PolySymbolCodeCompletionItem> =
    runCodeCompletionQuery(listOf(qualifiedKind.withName(name)), position, virtualSymbols, additionalScope)

  fun runCodeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    virtualSymbols: Boolean = true,
    additionalScope: List<PolySymbolsScope> = emptyList(),
  ): List<PolySymbolCodeCompletionItem>

  fun withNameConversionRules(rules: List<PolySymbolNameConversionRules>): PolySymbolsQueryExecutor

  fun hasExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind, scope: List<PolySymbolsScope> = emptyList()): Boolean

}