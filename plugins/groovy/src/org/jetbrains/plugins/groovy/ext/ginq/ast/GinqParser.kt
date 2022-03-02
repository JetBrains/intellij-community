// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.parents
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.ext.ginq.joins
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_IN
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
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
  var index = 1
  while (index < container.lastIndex) {
    val currentFragment = container[index]
    when (currentFragment) {
      is GinqJoinFragment -> joins.add(container[index] as GinqJoinFragment)
      is GinqWhereFragment -> where = currentFragment
    }
    index += 1
  }
  return GinqExpression(from, joins, where, null, null, null, select)
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
        val expr = if (callName == "from") {
          GinqFromFragment(callKw, alias, dataSource)
        }
        else {
          GinqJoinFragment(callKw, alias, dataSource, null)
        }
        container.add(expr)
      }
      "on", "where" -> {
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
        }
      }
      "select" -> {
        val arguments = methodCall.getExpressionArguments<GrExpression>()
        if (arguments == null) {
          recordError(methodCall, "Expected a list of projections")
          return
        }
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

private inline fun <reified T : GrExpression> GrMethodCall.getExpressionArguments(): List<T>? =
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

fun PsiElement.ginqParents(): Sequence<GinqExpression> = sequence {
  for (parent in parents(true)) {
    val rootGinq = parent.getUserData(rootGinq)
    if (rootGinq != null) {
      yield(rootGinq.value)
      return@sequence
    }
    val ginq = parent.getUserData(injectedGinq) ?: continue
    yield(ginq)
  }
}