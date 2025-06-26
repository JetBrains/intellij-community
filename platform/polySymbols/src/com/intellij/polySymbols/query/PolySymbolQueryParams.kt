// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.impl.AbstractQueryParamsBuilderImpl

sealed interface PolySymbolQueryParams {

  val framework: String?
    get() = queryExecutor.framework

  val queryExecutor: PolySymbolQueryExecutor

  val requiredModifiers: List<PolySymbolModifier>
  val excludeModifiers: List<PolySymbolModifier>

  fun accept(symbol: PolySymbol): Boolean {
    if (requiredModifiers.isNotEmpty() || excludeModifiers.isNotEmpty()) {
      val symbolModifiers = symbol.modifiers
      if (!requiredModifiers.all { symbolModifiers.contains(it) } || excludeModifiers.any { symbolModifiers.contains(it) })
        return false
    }
    return true
  }

  interface Builder<T> {
    fun require(modifier: PolySymbolModifier): T
    fun require(vararg modifiers: PolySymbolModifier): T
    fun require(modifiers: Collection<PolySymbolModifier>): T

    fun exclude(modifier: PolySymbolModifier): T
    fun exclude(vararg modifiers: PolySymbolModifier): T
    fun exclude(modifiers: Collection<PolySymbolModifier>): T

    fun copyFiltersFrom(params: PolySymbolQueryParams) {
      require(params.requiredModifiers)
      exclude(params.excludeModifiers)
    }
  }
}

sealed interface PolySymbolListSymbolsQueryParams : PolySymbolQueryParams {
  override val queryExecutor: PolySymbolQueryExecutor
  val expandPatterns: Boolean
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolQueryExecutor,
      expandPatterns: Boolean,
      configurator: Builder.() -> Unit = {},
    ): PolySymbolListSymbolsQueryParams =
      PolySymbolListSymbolsQueryParamsBuilderImpl(queryExecutor, expandPatterns).apply(configurator).build()
  }

  interface BuilderMixin<T> : PolySymbolQueryParams.Builder<T> {
    fun strictScope(value: Boolean): T
  }

  interface Builder : PolySymbolQueryParams.Builder<Builder>, BuilderMixin<Builder>
}


sealed interface PolySymbolNameMatchQueryParams : PolySymbolQueryParams {
  override val queryExecutor: PolySymbolQueryExecutor
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolQueryExecutor,
      configurator: Builder.() -> Unit = {},
    ): PolySymbolNameMatchQueryParams =
      PolySymbolNameMatchQueryParamsBuilderImpl(queryExecutor).apply(configurator).build()

  }

  interface BuilderMixin<T> {
    fun strictScope(value: Boolean): T
  }

  interface Builder : PolySymbolQueryParams.Builder<Builder>, BuilderMixin<Builder>
}

sealed interface PolySymbolCodeCompletionQueryParams : PolySymbolQueryParams {
  override val queryExecutor: PolySymbolQueryExecutor

  /** Position to complete at in the last segment of the path **/
  val position: Int

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolQueryExecutor,
      position: Int,
      configurator: Builder.() -> Unit = {},
    ): PolySymbolCodeCompletionQueryParams =
      PolySymbolCodeCompletionQueryParamsBuilderImpl(queryExecutor, position).apply(configurator).build()

  }

  interface BuilderMixin<T>

  interface Builder : PolySymbolQueryParams.Builder<Builder>, BuilderMixin<Builder>
}

private class PolySymbolListSymbolsQueryParamsBuilderImpl(
  private val queryExecutor: PolySymbolQueryExecutor,
  private val expandPatterns: Boolean,
) : PolySymbolListSymbolsQueryParams.Builder, AbstractQueryParamsBuilderImpl<PolySymbolListSymbolsQueryParams.Builder>() {
  private var strictScope: Boolean = false

  override fun strictScope(value: Boolean) = apply {
    this.strictScope = value
  }

  fun build(): PolySymbolListSymbolsQueryParams =
    PolySymbolListSymbolsQueryParamsData(queryExecutor, expandPatterns, strictScope, requiredModifiers.toList(),
                                         excludeModifiers.toList())

}

private class PolySymbolNameMatchQueryParamsBuilderImpl(
  private val queryExecutor: PolySymbolQueryExecutor,
) : PolySymbolNameMatchQueryParams.Builder, AbstractQueryParamsBuilderImpl<PolySymbolNameMatchQueryParams.Builder>() {
  private var strictScope: Boolean = false

  override fun strictScope(value: Boolean) = apply {
    this.strictScope = value
  }

  fun build(): PolySymbolNameMatchQueryParams =
    PolySymbolNameMatchQueryParamsData(queryExecutor, strictScope, requiredModifiers.toList(),
                                       excludeModifiers.toList())

}

private class PolySymbolCodeCompletionQueryParamsBuilderImpl(
  private val queryExecutor: PolySymbolQueryExecutor,
  private val position: Int,
) : PolySymbolCodeCompletionQueryParams.Builder, AbstractQueryParamsBuilderImpl<PolySymbolCodeCompletionQueryParams.Builder>() {

  fun build(): PolySymbolCodeCompletionQueryParams =
    PolySymbolCodeCompletionQueryParamsData(queryExecutor, position, requiredModifiers.toList(), excludeModifiers.toList())

}

internal data class PolySymbolCodeCompletionQueryParamsData(
  override val queryExecutor: PolySymbolQueryExecutor,
  override val position: Int,
  override val requiredModifiers: List<PolySymbolModifier>,
  override val excludeModifiers: List<PolySymbolModifier>,
) : PolySymbolCodeCompletionQueryParams

internal data class PolySymbolListSymbolsQueryParamsData(
  override val queryExecutor: PolySymbolQueryExecutor,
  override val expandPatterns: Boolean,
  override val strictScope: Boolean,
  override val requiredModifiers: List<PolySymbolModifier>,
  override val excludeModifiers: List<PolySymbolModifier>,
) : PolySymbolListSymbolsQueryParams

internal data class PolySymbolNameMatchQueryParamsData(
  override val queryExecutor: PolySymbolQueryExecutor,
  override val strictScope: Boolean,
  override val requiredModifiers: List<PolySymbolModifier>,
  override val excludeModifiers: List<PolySymbolModifier>,
) : PolySymbolNameMatchQueryParams