// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
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
data class GinqExpression(
  val from: GinqFromFragment,
  val joins: List<GinqJoinFragment>,
  val where: GinqWhereFragment?,
  val groupBy: GinqGroupByFragment?,
  val orderBy: GinqOrderByFragment?,
  val limit: GinqLimitFragment?,
  val select: GinqSelectFragment,
) {
  fun getDataSourceFragments(): Iterable<GinqDataSourceFragment> = listOf(from) + joins
}

sealed interface GinqQueryFragment

interface GinqDataSourceFragment {
  val alias: GrReferenceExpression
  val dataSource: GrExpression
}

data class GinqFromFragment(
  val fromKw: PsiElement,
  override val alias: GrReferenceExpression,
  override val dataSource: GrExpression,
) : GinqDataSourceFragment, GinqQueryFragment

data class GinqJoinFragment(
  val joinKw: PsiElement,
  override val alias: GrReferenceExpression,
  override val dataSource: GrExpression,
  val onCondition: GinqOnFragment?,
) : GinqDataSourceFragment, GinqQueryFragment

interface GinqFilterFragment : GinqQueryFragment {
  val filter: GrExpression
}

data class GinqOnFragment(
  val onKw: PsiElement,
  override val filter: GrBinaryExpression,
) : GinqFilterFragment, GinqQueryFragment

data class GinqWhereFragment(
  val whereKw: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment

data class GinqHavingFragment(
  val havingKw: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment

data class GinqGroupByFragment(
  val groupByKw: PsiElement,
  val classifiers: List<AliasedExpression>,
  val having: GinqHavingFragment?,
) : GinqQueryFragment

data class AliasedExpression(val expression: GrExpression, val alias: GrClassTypeElement?)

data class GinqOrderByFragment(
  val orderByKw: PsiElement,
  val sortingFields: List<Ordering>,
) : GinqQueryFragment

sealed interface Ordering {
  val orderKw: PsiElement?
  val nullsKw: PsiElement?
  val sorter: GrExpression

  class Asc internal constructor(override val orderKw: PsiElement?,
                                 override val nullsKw: PsiElement?,
                                 override val sorter: GrExpression) : Ordering

  class Desc internal constructor(override val orderKw: PsiElement?,
                                  override val nullsKw: PsiElement?,
                                  override val sorter: GrExpression) : Ordering

  companion object {
    fun from(expr: GrExpression): Ordering {
      if (expr is GrBinaryExpression && expr.operationTokenType == GroovyElementTypes.KW_IN) {
        val rightOperand = expr.rightOperand
        val (orderKw, nullsKw) = if (rightOperand is GrReferenceExpression) {
          rightOperand to null
        } else if (rightOperand is GrMethodCall && rightOperand.invokedExpression is GrReferenceExpression) {
          rightOperand.invokedExpression as GrReferenceExpression to
            rightOperand.argumentList.expressionArguments.singleOrNull()?.takeIf { it.text == "nullsfirst" || it.text == "nullslast" }
        } else null to null
        return when (orderKw?.referenceName) {
          "asc" -> Asc(orderKw, nullsKw, expr.leftOperand)
          "desc" -> Desc(orderKw, nullsKw, expr.leftOperand)
          else -> Asc(null, null, expr)
        }
      }
      else {
        return Asc(null, null, expr)
      }
    }
  }
}

data class GinqLimitFragment(
  val limitKw: PsiElement,
  val offset: GrExpression,
  val size: GrExpression?,
) : GinqQueryFragment

data class GinqSelectFragment(
  val selectKw: PsiElement,
  val distinct: GrReferenceExpression?,
  val projections: List<AggregatableAliasedExpression>,
) : GinqQueryFragment

data class AggregatableAliasedExpression(
  val aggregatedExpression: GrExpression,
  val alias: GrClassTypeElement?,
)