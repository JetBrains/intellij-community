// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
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

  fun isValid() : Boolean {
    return from.isValid() &&
           joins.all { it.isValid() } &&
           where?.isValid() ?: true &&
           groupBy?.isValid() ?: true &&
           orderBy?.isValid() ?: true &&
           limit?.isValid() ?: true &&
           select.isValid()
  }
}

sealed interface GinqQueryFragment {
  fun isValid() : Boolean
}

interface GinqDataSourceFragment {
  val alias: GrReferenceExpression
  val dataSource: GrExpression
}

data class GinqFromFragment(
  val fromKw: PsiElement,
  override val alias: GrReferenceExpression,
  override val dataSource: GrExpression,
) : GinqDataSourceFragment, GinqQueryFragment {
  override fun isValid() : Boolean = fromKw.isValid && alias.isValid && dataSource.isValid
}

data class GinqJoinFragment(
  val joinKw: PsiElement,
  override val alias: GrReferenceExpression,
  override val dataSource: GrExpression,
  val onCondition: GinqOnFragment?,
) : GinqDataSourceFragment, GinqQueryFragment {
  override fun isValid() : Boolean = joinKw.isValid && alias.isValid && dataSource.isValid
}

interface GinqFilterFragment : GinqQueryFragment {
  val filter: GrExpression
}

data class GinqOnFragment(
  val onKw: PsiElement,
  override val filter: GrBinaryExpression,
) : GinqFilterFragment, GinqQueryFragment {
  override fun isValid() : Boolean = onKw.isValid && filter.isValid
}

data class GinqWhereFragment(
  val whereKw: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment {
  override fun isValid() : Boolean = whereKw.isValid && filter.isValid
}

data class GinqHavingFragment(
  val havingKw: PsiElement,
  override val filter: GrExpression,
) : GinqFilterFragment, GinqQueryFragment {
  override fun isValid() : Boolean = havingKw.isValid && filter.isValid
}

data class GinqGroupByFragment(
  val groupByKw: PsiElement,
  val classifiers: List<AliasedExpression>,
  val having: GinqHavingFragment?,
) : GinqQueryFragment {
  override fun isValid() : Boolean =
    groupByKw.isValid && classifiers.all { it.alias?.isValid ?: true && it.expression.isValid } && having?.isValid() ?: true
}

data class AliasedExpression(val expression: GrExpression, val alias: GrClassTypeElement?)

data class GinqOrderByFragment(
  val orderByKw: PsiElement,
  val sortingFields: List<Ordering>,
) : GinqQueryFragment {
  override fun isValid() : Boolean = orderByKw.isValid && sortingFields.all { it.orderKw?.isValid ?: true && it.sorter.isValid }
}

sealed interface Ordering {
  val orderKw: PsiElement?
  val sorter: GrExpression

  class Asc internal constructor(override val orderKw: PsiElement?, override val sorter: GrExpression) : Ordering

  class Desc internal constructor(override val orderKw: PsiElement?, override val sorter: GrExpression) : Ordering

  companion object {
    fun from(expr: GrExpression): Ordering {
      if (expr is GrBinaryExpression && expr.operationTokenType == GroovyElementTypes.KW_IN) {
        val orderKw = expr.rightOperand?.castSafelyTo<GrReferenceExpression>()
        return when (orderKw?.referenceName) {
          "asc" -> Asc(orderKw, expr.leftOperand)
          "desc" -> Desc(orderKw, expr.leftOperand)
          else -> Asc(null, expr)
        }
      }
      else {
        return Asc(null, expr)
      }
    }
  }
}

data class GinqLimitFragment(
  val limitKw: PsiElement,
  val offset: GrExpression,
  val size: GrExpression?,
) : GinqQueryFragment {
  override fun isValid() : Boolean = limitKw.isValid && offset.isValid && size?.isValid ?: true
}

data class GinqSelectFragment(
  val selectKw: PsiElement,
  val projections: List<AliasedExpression>,
) : GinqQueryFragment {
  override fun isValid() : Boolean = selectKw.isValid && projections.all { it.alias?.isValid ?: true && it.expression.isValid }
}
