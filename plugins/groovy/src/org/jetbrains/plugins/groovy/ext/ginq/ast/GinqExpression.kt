// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import com.intellij.util.castSafelyTo
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement

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
  val classifiers: List<AliasedExpression>,
  val having: GinqHavingFragment?,
) : GinqQueryFragment

data class AliasedExpression(val classifier: GrExpression, val alias: GrTypeElement?)

data class GinqOrderByFragment(
  val orderByKw: PsiElement,
  val sortingFields: List<Ordering>,
) : GinqQueryFragment

sealed class Ordering private constructor(val orderKw: PsiElement?, val sorter: GrExpression) {

  class Asc internal constructor(orderKw: PsiElement?, sorter: GrExpression) : Ordering(orderKw, sorter)

  class Desc internal constructor(orderKw: PsiElement?, sorter: GrExpression) : Ordering(orderKw, sorter)

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
) : GinqQueryFragment

data class GinqSelectFragment(
  val selectKw: PsiElement,
  val projections: List<GrExpression>,
) : GinqQueryFragment
