// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.model.Pointer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolKind
import com.intellij.polySymbols.PolySymbolQualifiedName
import com.intellij.polySymbols.completion.PolySymbolCodeCompletionItem
import com.intellij.polySymbols.context.PolyContext
import com.intellij.psi.PsiElement

/**
 * To create a query executor use [PolySymbolQueryExecutorFactory].
 * The query executor will be configured by all the registered [PolySymbolQueryScopeContributor]s and
 * [PolySymbolQueryConfigurator]s based on the provided source code location.
 * Query scope contributors will provide initial Poly Symbol scopes,
 * and query configurators rules for calculating PolyContext and rules for symbol names conversion.
 */
/*
 * INAPPLICABLE_JVM_NAME -> https://youtrack.jetbrains.com/issue/KT-31420
 **/
@Suppress("INAPPLICABLE_JVM_NAME")
interface PolySymbolQueryExecutor : ModificationTracker {

  val location: PsiElement?

  val context: PolyContext

  @get:JvmName("allowResolve")
  val allowResolve: Boolean

  val namesProvider: PolySymbolNamesProvider

  val resultsCustomizer: PolySymbolQueryResultsCustomizer

  var keepUnresolvedTopLevelReferences: Boolean

  fun createPointer(): Pointer<PolySymbolQueryExecutor>

  fun nameMatchQuery(
    path: List<PolySymbolQualifiedName>,
  ): NameMatchQueryBuilder

  fun listSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    kind: PolySymbolKind,
    expandPatterns: Boolean,
  ): ListSymbolsQueryBuilder

  fun codeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    /** Position to complete at in the last segment of the path **/
    position: Int,
  ): CodeCompletionQueryBuilder

  fun nameMatchQuery(
    kind: PolySymbolKind,
    name: String,
  ): NameMatchQueryBuilder =
    nameMatchQuery(listOf(kind.withName(name)))

  fun nameMatchQuery(
    path: List<PolySymbolQualifiedName>,
    configurator: NameMatchQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    nameMatchQuery(path).apply(configurator).run()

  fun nameMatchQuery(
    kind: PolySymbolKind,
    name: String,
    configurator: NameMatchQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    nameMatchQuery(listOf(kind.withName(name))).apply(configurator).run()

  fun listSymbolsQuery(
    kind: PolySymbolKind,
    expandPatterns: Boolean,
  ): ListSymbolsQueryBuilder =
    listSymbolsQuery(emptyList(), kind, expandPatterns)

  fun listSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    kind: PolySymbolKind,
    expandPatterns: Boolean,
    configurator: ListSymbolsQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    listSymbolsQuery(path, kind, expandPatterns).apply(configurator).run()

  fun listSymbolsQuery(
    kind: PolySymbolKind,
    expandPatterns: Boolean = false,
    configurator: ListSymbolsQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    listSymbolsQuery(emptyList(), kind, expandPatterns).apply(configurator).run()

  fun codeCompletionQuery(
    kind: PolySymbolKind,
    name: String,
    /** Position to complete at in the last segment of the path **/
    position: Int,
  ): CodeCompletionQueryBuilder =
    codeCompletionQuery(listOf(kind.withName(name)), position)

  fun codeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    configurator: CodeCompletionQueryBuilder.() -> Unit,
  ): List<PolySymbolCodeCompletionItem> =
    codeCompletionQuery(path, position).apply(configurator).run()

  fun codeCompletionQuery(
    kind: PolySymbolKind,
    name: String,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    configurator: CodeCompletionQueryBuilder.() -> Unit,
  ): List<PolySymbolCodeCompletionItem> =
    codeCompletionQuery(listOf(kind.withName(name)), position).apply(configurator).run()

  fun withNameConversionRules(rules: List<PolySymbolNameConversionRules>): PolySymbolQueryExecutor

  fun hasExclusiveScopeFor(kind: PolySymbolKind, scope: List<PolySymbolScope> = emptyList()): Boolean

  interface QueryBuilder<T> : PolySymbolQueryParams.Builder<T> {
    fun additionalScope(scope: PolySymbolScope): T
    fun additionalScope(vararg scopes: PolySymbolScope): T
    fun additionalScope(scopes: Collection<PolySymbolScope>): T
    fun additionalScope(stack: PolySymbolQueryStack): T
  }

  interface NameMatchQueryBuilder : QueryBuilder<NameMatchQueryBuilder>,
                                    PolySymbolNameMatchQueryParams.BuilderMixin<NameMatchQueryBuilder> {
    fun run(): List<PolySymbol>
  }

  interface ListSymbolsQueryBuilder : QueryBuilder<ListSymbolsQueryBuilder>,
                                      PolySymbolListSymbolsQueryParams.BuilderMixin<ListSymbolsQueryBuilder> {
    fun run(): List<PolySymbol>
  }

  interface CodeCompletionQueryBuilder : QueryBuilder<CodeCompletionQueryBuilder>,
                                         PolySymbolCodeCompletionQueryParams.BuilderMixin<CodeCompletionQueryBuilder> {
    fun run(): List<PolySymbolCodeCompletionItem>
  }

}