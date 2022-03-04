// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.parents
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.ext.ginq.GinqMacroTransformationSupport
import org.jetbrains.plugins.groovy.ext.ginq.joins
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_IN
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

fun parseGinq(statementsOwner: GrStatementOwner): GinqExpression? {
  val parser = GinqParser()
  statementsOwner.statements.forEach { it.accept(parser) }
  return gatherGinqExpression(parser.container)
}

fun parseGinqAsExpr(psiGinq: GrExpression): GinqExpression? =
  GinqParser().also(psiGinq::accept).run { gatherGinqExpression(container) }

private fun gatherGinqExpression(container: List<GinqQueryFragment>): GinqExpression? {
  if (container.size < 2) {
    return null
  }
  val from = container.first() as? GinqFromFragment ?: return null
  val select = container.last() as? GinqSelectFragment ?: return null
  // otherwise it is a valid ginq expression
  val joins: MutableList<GinqJoinFragment> = mutableListOf()
  var where: GinqWhereFragment? = null
  var groupBy: GinqGroupByFragment? = null
  var orderBy: GinqOrderByFragment? = null
  var limit: GinqLimitFragment? = null
  var index = 1
  while (index < container.lastIndex) {
    val currentFragment = container[index]
    when (currentFragment) {
      is GinqJoinFragment -> joins.add(container[index] as GinqJoinFragment)
      is GinqWhereFragment -> where = currentFragment
      is GinqGroupByFragment -> groupBy = currentFragment
      is GinqOrderByFragment -> orderBy = currentFragment
      is GinqLimitFragment -> limit = currentFragment
    }
    index += 1
  }
  return GinqExpression(from, joins, where, groupBy, orderBy, limit, select)
}

/**
 * **See:** org.apache.groovy.ginq.dsl.GinqAstBuilder
 */
private class GinqParser : GroovyRecursiveElementVisitor() {
  val container: MutableList<GinqQueryFragment> = mutableListOf()
  private val errors: MutableList<Pair<PsiElement, @Nls String>> = mutableListOf()

  override fun visitMethodCall(methodCall: GrMethodCall) { // todo: i18n
    super.visitMethodCall(methodCall)
    val callName = methodCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName
    if (callName == null) {
      recordError(methodCall.invokedExpression, "Expected method call")
      return
    }
    val callKw = methodCall.refCallIdentifier()
    when (callName) {
      "from", in joins -> {
        val argument = methodCall.getSingleArgument<GrBinaryExpression>()?.takeIf { it.operationTokenType == KW_IN }
        if (argument == null) {
          recordError(methodCall, "Expected ... in ...")
          return
        }
        val alias = argument.leftOperand.castSafelyTo<GrReferenceExpression>()
        if (alias == null) {
          recordError(argument.leftOperand, "Expected alias name")
        }
        val dataSource = argument.rightOperand
        if (dataSource == null) {
          recordError(argument.operationToken, "Expected data source")
        }
        if (alias == null || dataSource == null) {
          return
        }
        dataSource.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
        val expr = if (callName == "from") {
          GinqFromFragment(callKw, alias, dataSource)
        }
        else {
          GinqJoinFragment(callKw, alias, dataSource, null)
        }
        container.add(expr)
      }
      "on", "where", "having" -> {
        val argument = methodCall.getSingleArgument<GrExpression>()
        if (argument == null) {
          recordError(methodCall, "Expected a list of conditions")
          return
        }
        if (callName == "on") {
          val last = container.lastOrNull()
          if (last is GinqJoinFragment && last.onCondition == null && argument is GrBinaryExpression) {
            val newJoin = GinqJoinFragment(last.joinKw, last.alias, last.dataSource, GinqOnFragment(callKw, argument))
            container.removeLast()
            container.add(newJoin)
          }
          else {
            recordError(methodCall, "`on` is expected after `join`")
          }
        }
        else if (callName == "where") {
          container.add(GinqWhereFragment(callKw, argument))
        } else if (callName == "having") {
          val last = container.lastOrNull()
          if (last is GinqGroupByFragment && last.having == null) {
            val newGroupBy = last.copy(having = GinqHavingFragment(callKw, argument))
            container.removeLast()
            container.add(newGroupBy)
          }
        }
      }
      "groupby" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map { if (it is GrSafeCastExpression) AliasedExpression(it.operand, it.castTypeElement) else AliasedExpression(it, null) }
        if (arguments == null) {
          recordError(methodCall, "Expected a classifier argument")
          return
        }
        container.add(GinqGroupByFragment(callKw, arguments, null))
      }
      "orderby" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map(Ordering.Companion::from)
        if (arguments == null) {
          recordError(methodCall, "Expected a list of order fields")
          return
        }
        container.add(GinqOrderByFragment(callKw, arguments))
      }
      "limit" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()
        if (arguments == null || arguments.isEmpty() || arguments.size > 2) {
          recordError(methodCall, "Expected one or two arguments for 'limit'")
          return
        }
        container.add(GinqLimitFragment(callKw, arguments[0], arguments.getOrNull(1)))
      }
      "select" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()
        if (arguments == null) {
          recordError(methodCall, "Expected a list of projections")
          return
        }
        arguments.forEach { it.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit) }
        container.add(GinqSelectFragment(callKw, arguments))
      }
      else -> recordError(methodCall, "Unrecognized query")
    }
  }

  override fun visitArgumentList(list: GrArgumentList) {
    for (argument in list.expressionArguments) {
      val ginqExpression =
        if (!isApproximatelyGinq(argument)) null
        else {
          parseGinqAsExpr(argument)
        }
      if (ginqExpression == null) {
        super.visitArgumentList(list)
      } else {
        list.putUserData(injectedGinq, ginqExpression)
      }
    }
  }

  private fun recordError(element: PsiElement, message: @Nls String) {}

}

private inline fun <reified T : GrExpression> GrMethodCall.getSingleArgument(): T? =
  this.getArguments()?.singleOrNull()?.castSafelyTo<ExpressionArgument>()?.expression?.castSafelyTo<T>()

private inline fun <reified T : GrExpression> GrMethodCall.collectExpressionArguments(): List<T>? =
  this.getArguments()?.filterIsInstance<ExpressionArgument>()?.map { it.expression }?.filterIsInstance<T>()?.takeIf { it.size == getArguments()?.size }

private fun GrMethodCall.refCallIdentifier(): PsiElement = invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceNameElement
                                                           ?: invokedExpression

// e is not ginq --> false
private fun isApproximatelyGinq(e: PsiElement): Boolean {
  val text = e.text
  return text.contains("from") && text.contains("select")
}

val injectedGinq : Key<GinqExpression> = Key.create("injected ginq expression")
val rootGinq: Key<CachedValue<GinqExpression>> = Key.create("root ginq expression")

fun PsiElement.ginqParents(top: PsiElement, topExpr: GinqExpression): Sequence<GinqExpression> = sequence {
  for (parent in parents(true)) {
    if (parent == top) {
      yield(topExpr)
      return@sequence
    }
    val ginq = parent.getUserData(injectedGinq) ?: continue
    yield(ginq)
  }
}