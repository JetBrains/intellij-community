// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

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
data class GinqExpression(
  val from: GinqFromFragment,
  val join: List<GinqJoinFragment>,
  val where: GinqWhereFragment?,
  val groupBy: GinqGroupByFragment?,
  val orderBy: GinqOrderByFragment?,
  val limit: GinqLimitFragment?,
  val select: GinqSelectFragment,
)

sealed interface GinqQueryFragment

abstract class GinqDataSourceFragment(
  val alias: GrReferenceExpression,
  val dataSource: GrExpression,
)

class GinqFromFragment(
  val fromKw: PsiElement,
  alias: GrReferenceExpression,
  dataSource: GrExpression,
) : GinqDataSourceFragment(alias, dataSource), GinqQueryFragment

class GinqJoinFragment(
  val joinKw: PsiElement,
  aliasExpression: GrReferenceExpression,
  dataSourceExpression: GrExpression,
  val onCondition: GinqOnFragment?,
) : GinqDataSourceFragment(aliasExpression, dataSourceExpression), GinqQueryFragment

abstract class GinqFilterFragment(open val filter: GrExpression) : GinqQueryFragment

class GinqOnFragment(
  val onKw: PsiElement,
  filterExpression: GrBinaryExpression,
) : GinqFilterFragment(filterExpression), GinqQueryFragment {
  override val filter: GrBinaryExpression = filterExpression
}

class GinqWhereFragment(
  val whereKw: PsiElement,
  filterExpression: GrExpression,
) : GinqFilterFragment(filterExpression), GinqQueryFragment

class GinqHavingFragment(
  val havingKw: PsiElement,
  filterExpression: GrExpression,
) : GinqFilterFragment(filterExpression), GinqQueryFragment

data class GinqGroupByFragment(
  val groupByKw: PsiElement,
  val classifier: GrExpression,
  val having: GinqHavingFragment,
) : GinqQueryFragment

data class GinqOrderByFragment(
  val orderByKw: PsiElement,
  val orders: GrExpression,
) : GinqQueryFragment

data class GinqLimitFragment(
  val limitKw: PsiElement,
  val offsetAndSize: GrExpression,
) : GinqQueryFragment

data class GinqSelectFragment(
  val selectKw: PsiElement,
  val projections: List<GrExpression>,
) : GinqQueryFragment
