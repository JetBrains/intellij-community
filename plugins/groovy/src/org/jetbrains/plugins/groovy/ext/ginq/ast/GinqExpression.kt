// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement

/**
 *
 * Represents GINQ expression, which has the following structure:
 * ```
 *     GQ, i.e. abbreviation for GINQ
 *     |__ from
 *     |   |__ <data_source_alias> in <data_source>
 *     |__ [join/innerjoin/leftjoin/rightjoin/fulljoin/crossjoin]*
 *     |   |__ <data_source_alias> in <data_source>
 *     |   |__ on <condition> ((&& | ||) <condition>)* (NOTE: `crossjoin` does not need `on` clause)
 *     |__ [where]
 *     |   |__ <condition> ((&& | ||) <condition>)*
 *     |__ [groupby]
 *     |   |__ <expression> [as <alias>] (, <expression> [as <alias>])*
 *     |   |__ [having]
 *     |       |__ <condition> ((&& | ||) <condition>)*
 *     |__ [orderby]
 *     |   |__ <expression> [in (asc|desc)] (, <expression> [in (asc|desc)])*
 *     |__ [limit]
 *     |   |__ [<offset>,] <size>
 *     |__ select
 *     |__ <expression> [as <alias>] (, <expression> [as <alias>])*
 * ```
 * (**Note:** [] means the related clause is optional,
 * `*` means zero or more times, and + means one or more times.
 * Also, the clauses of GINQ are order sensitive,
 * so the order of clauses should be kept as the above structure)
 *
 * (**See:** org.apache.groovy.ginq.dsl.expression.GinqExpression)
 */
internal data class GinqExpression(
  val from: GinqFromFragment,
  val joins: List<GinqJoinFragment>,
  val where: GinqWhereFragment?,
  val groupBy: GinqGroupByFragment?,
  val orderBy: GinqOrderByFragment?,
  val limit: GinqLimitFragment?,
  val select: GinqSelectFragment?,
) : GenericGinqExpression {
  fun getDataSourceFragments(): Iterable<GinqDataSourceFragment> = listOf(from) + joins

  fun getFilterFragments(): Iterable<GinqFilterFragment> = listOfNotNull(where, groupBy?.having) + joins.mapNotNull { it.onCondition }

  fun getQueryFragments(): Iterable<GinqQueryFragment> = listOfNotNull(from, where, groupBy, groupBy?.having, orderBy, limit, select) + joins + joins.mapNotNull { it.onCondition }
}

sealed interface GenericGinqExpression

/**
 * GQ { shutdown [immediate|abort] }
 */
data class GinqShutdown(val shutdownKw: PsiElement, val optionKw: PsiElement?) : GenericGinqExpression

sealed interface GinqQueryFragment {
  val keyword: PsiElement
}

sealed interface GinqDataSourceFragment {
  val alias: GrReferenceExpression
  val dataSource: GrExpression
}

data class GinqFromFragment(
  override val keyword: PsiElement,
  override val alias: GrReferenceExpression,
  override val dataSource: GrExpression,
) : GinqDataSourceFragment, GinqQueryFragment

data class GinqJoinFragment(
  override val keyword: PsiElement,
  override val alias: GrReferenceExpression,
  override val dataSource: GrExpression,
  val onCondition: GinqOnFragment?,
) : GinqDataSourceFragment, GinqQueryFragment

sealed interface GinqFilterFragment {
  val filter: GrExpression
}

data class GinqOnFragment(
  override val keyword: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment

data class GinqWhereFragment(
  override val keyword: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment

data class GinqHavingFragment(
  override val keyword: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment

data class GinqGroupByFragment(
  override val keyword: PsiElement,
  val classifiers: List<AliasedExpression>,
  val having: GinqHavingFragment?,
) : GinqQueryFragment

data class AliasedExpression(val expression: GrExpression, val alias: GrClassTypeElement?)

internal data class GinqOrderByFragment(
  override val keyword: PsiElement,
  val sortingFields: List<Ordering>,
) : GinqQueryFragment

internal sealed interface Ordering {
  val orderKw: PsiElement?
  val nullsKw: PsiElement?
  val sorter: GrExpression

  data class Asc(
    override val orderKw: PsiElement?,
    override val nullsKw: PsiElement?,
    override val sorter: GrExpression,
  ) : Ordering

  data class Desc(
    override val orderKw: PsiElement?,
    override val nullsKw: PsiElement?,
    override val sorter: GrExpression,
  ) : Ordering
}

data class GinqLimitFragment(
  override val keyword: PsiElement,
  val offset: GrExpression,
  val size: GrExpression?,
) : GinqQueryFragment

internal data class GinqSelectFragment(
  override val keyword: PsiElement,
  val distinct: GrReferenceExpression?,
  val projections: List<AggregatableAliasedExpression>,
) : GinqQueryFragment

internal data class AggregatableAliasedExpression(
  val aggregatedExpression: GrExpression,
  val windows: List<GinqWindowFragment>,
  val alias: GrClassTypeElement?,
)

/**
 * over(
 *   [partitionby <expression> (, <expression>)*]
 *   [orderby (<expression> [in asc | in desc]) (, <expression> [in asc | in desc])*
 *      [rows <lower>, <upper> | range <lower>, <upper>]]
 * )
 */
internal data class GinqWindowFragment(
  val qualifier: GrExpression,
  val overKw: PsiElement,
  val partitionKw: PsiElement?,
  val partitionArguments: List<GrExpression>,
  val orderBy: GinqOrderByFragment?,
  val rowsOrRangeKw: PsiElement?,
  val rowsOrRangeArguments: List<GrExpression>,
)