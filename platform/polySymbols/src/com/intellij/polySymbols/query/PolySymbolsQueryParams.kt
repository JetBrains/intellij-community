// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols.query

import com.intellij.polySymbols.PolySymbol
import com.intellij.polySymbols.PolySymbolAccessModifier
import com.intellij.polySymbols.PolySymbolModifier
import com.intellij.polySymbols.query.impl.AbstractQueryParamsBuilderImpl

sealed interface PolySymbolsQueryParams {

  val framework: String?
    get() = queryExecutor.framework

  val queryExecutor: PolySymbolsQueryExecutor

  val requiredModifiers: List<PolySymbolModifier>
  val requiredAccessModifier: PolySymbolAccessModifier?
  val excludeModifiers: List<PolySymbolModifier>
  val excludeAccessModifiers: List<PolySymbolAccessModifier>

  fun accept(symbol: PolySymbol): Boolean {
    if (requiredModifiers.isNotEmpty() || excludeModifiers.isNotEmpty()) {
      val symbolModifiers = symbol.modifiers
      if (!requiredModifiers.all { symbolModifiers.contains(it) } || excludeModifiers.any { symbolModifiers.contains(it) })
        return false
    }
    if (requiredAccessModifier != null || excludeAccessModifiers.isNotEmpty()) {
      val symbolAccessModifier = symbol.accessModifier
      if ((requiredAccessModifier != null && symbolAccessModifier != requiredAccessModifier) || excludeAccessModifiers.contains(symbolAccessModifier))
        return false
    }
    return true
  }

  interface Builder<T> {
    fun require(modifier: PolySymbolModifier): T
    fun require(vararg modifiers: PolySymbolModifier): T
    fun require(modifiers: Collection<PolySymbolModifier>): T

    fun requireAccess(modifier: PolySymbolAccessModifier): T

    fun exclude(modifier: PolySymbolModifier): T
    fun exclude(vararg modifiers: PolySymbolModifier): T
    fun exclude(modifiers: Collection<PolySymbolModifier>): T

    fun excludeAccess(modifier: PolySymbolAccessModifier): T
    fun excludeAccess(vararg modifiers: PolySymbolAccessModifier): T
    fun excludeAccess(modifiers: Collection<PolySymbolAccessModifier>): T

    fun copyFiltersFrom(params: PolySymbolsQueryParams) {
      require(params.requiredModifiers)
      params.requiredAccessModifier?.let { requireAccess(it) }
      exclude(params.excludeModifiers)
      excludeAccess(params.excludeAccessModifiers)
    }
  }
}

sealed interface PolySymbolsListSymbolsQueryParams : PolySymbolsQueryParams {
  override val queryExecutor: PolySymbolsQueryExecutor
  val expandPatterns: Boolean
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      expandPatterns: Boolean,
      configurator: Builder.() -> Unit = {},
    ): PolySymbolsListSymbolsQueryParams =
      PolySymbolsListSymbolsQueryParamsBuilderImpl(queryExecutor, expandPatterns).apply(configurator).build()
  }

  interface BuilderMixin<T> : PolySymbolsQueryParams.Builder<T> {
    fun strictScope(value: Boolean): T
  }

  interface Builder : PolySymbolsQueryParams.Builder<Builder>, BuilderMixin<Builder>
}


sealed interface PolySymbolsNameMatchQueryParams : PolySymbolsQueryParams {
  override val queryExecutor: PolySymbolsQueryExecutor
  val strictScope: Boolean

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      configurator: Builder.() -> Unit = {},
    ): PolySymbolsNameMatchQueryParams =
      PolySymbolsNameMatchQueryParamsBuilderImpl(queryExecutor).apply(configurator).build()

  }

  interface BuilderMixin<T> {
    fun strictScope(value: Boolean): T
  }

  interface Builder : PolySymbolsQueryParams.Builder<Builder>, BuilderMixin<Builder>
}

sealed interface PolySymbolsCodeCompletionQueryParams : PolySymbolsQueryParams {
  override val queryExecutor: PolySymbolsQueryExecutor

  /** Position to complete at in the last segment of the path **/
  val position: Int

  companion object {

    @JvmStatic
    @JvmOverloads
    fun create(
      queryExecutor: PolySymbolsQueryExecutor,
      position: Int,
      configurator: Builder.() -> Unit = {},
    ): PolySymbolsCodeCompletionQueryParams =
      PolySymbolsCodeCompletionQueryParamsBuilderImpl(queryExecutor, position).apply(configurator).build()

  }

  interface BuilderMixin<T>

  interface Builder : PolySymbolsQueryParams.Builder<Builder>, BuilderMixin<Builder>
}

private class PolySymbolsListSymbolsQueryParamsBuilderImpl(
  private val queryExecutor: PolySymbolsQueryExecutor,
  private val expandPatterns: Boolean,
) : PolySymbolsListSymbolsQueryParams.Builder, AbstractQueryParamsBuilderImpl<PolySymbolsListSymbolsQueryParams.Builder>() {
  private var strictScope: Boolean = false

  override fun strictScope(value: Boolean) = apply {
    this.strictScope = value
  }

  fun build(): PolySymbolsListSymbolsQueryParams =
    PolySymbolsListSymbolsQueryParamsData(queryExecutor, expandPatterns, strictScope, requiredModifiers.toList(), requiredAccessModifier,
                                          excludeModifiers.toList(), excludeAccessModifiers.toList())

}

private class PolySymbolsNameMatchQueryParamsBuilderImpl(
  private val queryExecutor: PolySymbolsQueryExecutor,
) : PolySymbolsNameMatchQueryParams.Builder, AbstractQueryParamsBuilderImpl<PolySymbolsNameMatchQueryParams.Builder>() {
  private var strictScope: Boolean = false

  override fun strictScope(value: Boolean) = apply {
    this.strictScope = value
  }

  fun build(): PolySymbolsNameMatchQueryParams =
    PolySymbolsNameMatchQueryParamsData(queryExecutor, strictScope, requiredModifiers.toList(), requiredAccessModifier,
                                        excludeModifiers.toList(), excludeAccessModifiers.toList())

}

private class PolySymbolsCodeCompletionQueryParamsBuilderImpl(
  private val queryExecutor: PolySymbolsQueryExecutor,
  private val position: Int,
) : PolySymbolsCodeCompletionQueryParams.Builder, AbstractQueryParamsBuilderImpl<PolySymbolsCodeCompletionQueryParams.Builder>() {

  fun build(): PolySymbolsCodeCompletionQueryParams =
    PolySymbolsCodeCompletionQueryParamsData(queryExecutor, position, requiredModifiers.toList(), requiredAccessModifier,
                                             excludeModifiers.toList(), excludeAccessModifiers.toList())

}

internal data class PolySymbolsCodeCompletionQueryParamsData(
  override val queryExecutor: PolySymbolsQueryExecutor,
  override val position: Int,
  override val requiredModifiers: List<PolySymbolModifier>,
  override val requiredAccessModifier: PolySymbolAccessModifier?,
  override val excludeModifiers: List<PolySymbolModifier>,
  override val excludeAccessModifiers: List<PolySymbolAccessModifier>,
) : PolySymbolsCodeCompletionQueryParams

internal data class PolySymbolsListSymbolsQueryParamsData(
  override val queryExecutor: PolySymbolsQueryExecutor,
  override val expandPatterns: Boolean,
  override val strictScope: Boolean,
  override val requiredModifiers: List<PolySymbolModifier>,
  override val requiredAccessModifier: PolySymbolAccessModifier?,
  override val excludeModifiers: List<PolySymbolModifier>,
  override val excludeAccessModifiers: List<PolySymbolAccessModifier>,
) : PolySymbolsListSymbolsQueryParams {
}

internal data class PolySymbolsNameMatchQueryParamsData(
  override val queryExecutor: PolySymbolsQueryExecutor,
  override val strictScope: Boolean,
  override val requiredModifiers: List<PolySymbolModifier>,
  override val requiredAccessModifier: PolySymbolAccessModifier?,
  override val excludeModifiers: List<PolySymbolModifier>,
  override val excludeAccessModifiers: List<PolySymbolAccessModifier>,
) : PolySymbolsNameMatchQueryParams