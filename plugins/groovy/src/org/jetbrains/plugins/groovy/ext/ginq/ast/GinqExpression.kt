// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

/**
 *
 * Represents GINQ expression, which has the following structure:
 * ```
 *     ginq
 *     |__ from
 *     |__ [innerjoin/leftjoin/rightjoin/fulljoin/crossjoin]*
 *     |   |__ on
 *     |__ [where]
 *     |__ [groupby]
 *     |   |__ [having]
 *     |__ [orderby]
 *     |__ [limit]
 *     |__ select
 * ```
 * (**Note:** [ ] means optional)
 *
 * (**See:** org.apache.groovy.ginq.dsl.expression.GinqExpression)
 */
data class GinqExpression(
  val fromExpression: GinqFromExpression,
  val joinExpressions: List<GinqJoinExpression>,
  val whereExpression: GinqWhereExpression?,
  val groupByExpression: GinqGroupByExpression?,
  val orderByExpression: GinqOrderByExpression?,
  val limitExpression: GinqLimitExpression?,
  val selectExpression: GinqSelectExpression,
)

sealed interface GinqQueryFragment

abstract class GinqDataSourceExpression(
  val aliasExpression: GrReferenceExpression,
  val dataSourceExpression: GrExpression,
)

class GinqFromExpression(
  val fromKw: PsiElement,
  aliasExpression: GrReferenceExpression,
  dataSourceExpression: GrExpression,
) : GinqDataSourceExpression(aliasExpression, dataSourceExpression), GinqQueryFragment

class GinqJoinExpression(
  val joinKw: PsiElement,
  aliasExpression: GrReferenceExpression,
  dataSourceExpression: GrExpression,
  val onCondition: GinqOnExpression?,
) : GinqDataSourceExpression(aliasExpression, dataSourceExpression), GinqQueryFragment

abstract class GinqFilterExpression(val filterExpression: GrExpression) : GinqQueryFragment

class GinqOnExpression(
  val onKw: PsiElement,
  filterExpression: GrExpression,
) : GinqFilterExpression(filterExpression), GinqQueryFragment

class GinqWhereExpression(
  val whereKw: PsiElement,
  filterExpression: GrExpression,
) : GinqFilterExpression(filterExpression), GinqQueryFragment

class GinqHavingExpression(
  val havingKw: PsiElement,
  filterExpression: GrExpression,
) : GinqFilterExpression(filterExpression), GinqQueryFragment

data class GinqGroupByExpression(
  val groupByKw: PsiElement,
  val classifier: GrExpression,
  val having: GinqHavingExpression,
) : GinqQueryFragment

data class GinqOrderByExpression(
  val orderByKw: PsiElement,
  val orders: GrExpression,
) : GinqQueryFragment

data class GinqLimitExpression(
  val limitKw: PsiElement,
  val offsetAndSize: GrExpression,
) : GinqQueryFragment

data class GinqSelectExpression(
  val selectKw: PsiElement,
  val projections: List<GrExpression>,
) : GinqQueryFragment
