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

  fun nameMatchQuery(
    path: List<PolySymbolQualifiedName>,
  ): NameMatchQueryBuilder

  fun listSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
  ): ListSymbolsQueryBuilder

  fun codeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    /** Position to complete at in the last segment of the path **/
    position: Int,
  ): CodeCompletionQueryBuilder

  fun nameMatchQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
  ): NameMatchQueryBuilder =
    nameMatchQuery(listOf(qualifiedKind.withName(name)))

  fun nameMatchQuery(
    path: List<PolySymbolQualifiedName>,
    configurator: NameMatchQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    nameMatchQuery(path).apply(configurator).run()

  fun nameMatchQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
    configurator: NameMatchQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    nameMatchQuery(listOf(qualifiedKind.withName(name))).apply(configurator).run()

  fun listSymbolsQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
  ): ListSymbolsQueryBuilder =
    listSymbolsQuery(emptyList(), qualifiedKind, expandPatterns)

  fun listSymbolsQuery(
    path: List<PolySymbolQualifiedName>,
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean,
    configurator: ListSymbolsQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    listSymbolsQuery(path, qualifiedKind, expandPatterns).apply(configurator).run()

  fun listSymbolsQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    expandPatterns: Boolean = false,
    configurator: ListSymbolsQueryBuilder.() -> Unit,
  ): List<PolySymbol> =
    listSymbolsQuery(emptyList(), qualifiedKind, expandPatterns).apply(configurator).run()

  fun codeCompletionQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
    /** Position to complete at in the last segment of the path **/
    position: Int,
  ): CodeCompletionQueryBuilder =
    codeCompletionQuery(listOf(qualifiedKind.withName(name)), position)

  fun codeCompletionQuery(
    path: List<PolySymbolQualifiedName>,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    configurator: CodeCompletionQueryBuilder.() -> Unit,
  ): List<PolySymbolCodeCompletionItem> =
    codeCompletionQuery(path, position).apply(configurator).run()

  fun codeCompletionQuery(
    qualifiedKind: PolySymbolQualifiedKind,
    name: String,
    /** Position to complete at in the last segment of the path **/
    position: Int,
    configurator: CodeCompletionQueryBuilder.() -> Unit,
  ): List<PolySymbolCodeCompletionItem> =
    codeCompletionQuery(listOf(qualifiedKind.withName(name)), position).apply(configurator).run()

  fun withNameConversionRules(rules: List<PolySymbolNameConversionRules>): PolySymbolsQueryExecutor

  fun hasExclusiveScopeFor(qualifiedKind: PolySymbolQualifiedKind, scope: List<PolySymbolsScope> = emptyList()): Boolean

  interface QueryBuilder<T>: PolySymbolsQueryParams.Builder<T> {
    fun additionalScope(scope: PolySymbolsScope): T
    fun additionalScope(vararg scopes: PolySymbolsScope): T
    fun additionalScope(scopes: Collection<PolySymbolsScope>): T
  }

  interface NameMatchQueryBuilder : QueryBuilder<NameMatchQueryBuilder>, PolySymbolsNameMatchQueryParams.BuilderMixin<NameMatchQueryBuilder> {
    fun run(): List<PolySymbol>
  }

  interface ListSymbolsQueryBuilder : QueryBuilder<ListSymbolsQueryBuilder>, PolySymbolsListSymbolsQueryParams.BuilderMixin<ListSymbolsQueryBuilder> {
    fun run(): List<PolySymbol>
  }

  interface CodeCompletionQueryBuilder : QueryBuilder<CodeCompletionQueryBuilder>, PolySymbolsCodeCompletionQueryParams.BuilderMixin<CodeCompletionQueryBuilder> {
    fun run(): List<PolySymbolCodeCompletionItem>
  }

}