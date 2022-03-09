// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.ext.ginq.GinqMacroTransformationSupport
import org.jetbrains.plugins.groovy.ext.ginq.joins
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_IN
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.util.skipParenthesesDownOrNull
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments
import org.jetbrains.plugins.groovy.lang.resolve.markAsReferenceResolveTarget

fun parseGinq(statementsOwner: GrStatementOwner): Pair<List<ParsingError>, GinqExpression?> {
  val parser = GinqParser()
  statementsOwner.statements.forEach { it.accept(parser) }
  return gatherGinqExpression(parser.errors, parser.container)
}

fun parseGinqAsExpr(psiGinq: GrExpression): Pair<List<ParsingError>, GinqExpression?> =
  GinqParser().also(psiGinq::accept).run { gatherGinqExpression(errors, container) }

private fun gatherGinqExpression(errors: List<ParsingError>,
                                 container: List<GinqQueryFragment>): Pair<List<ParsingError>, GinqExpression?> {
  if (container.size < 2) {
    return emptyList<ParsingError>() to null
  }
  val from = container.first() as? GinqFromFragment ?: return emptyList<ParsingError>() to null
  val select = container.last() as? GinqSelectFragment ?: return emptyList<ParsingError>() to null
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
  return errors to GinqExpression(from, joins, where, groupBy, orderBy, limit, select)
}

/**
 * **See:** org.apache.groovy.ginq.dsl.GinqAstBuilder
 */
private class GinqParser : GroovyRecursiveElementVisitor() {
  val container: MutableList<GinqQueryFragment> = mutableListOf()
  val errors: MutableList<ParsingError> = mutableListOf()
  var isTopLevel = true

  override fun visitMethodCall(methodCall: GrMethodCall) {
    if (isTopLevel) {
      methodCall.invokedExpression.accept(this)
      val currentTopLevel = isTopLevel
      isTopLevel = false
      methodCall.argumentList.accept(this)
      isTopLevel = currentTopLevel
    } else {
      return super.visitMethodCall(methodCall)
    }
    // todo: i18n
    if (methodCall.getStoredGinq() != null) {
      // was parsed as standalone ginq somewhere above
      return
    }
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
          recordError(methodCall, "Expected '... in ...'")
          return
        }
        val alias = argument.leftOperand.castSafelyTo<GrReferenceExpression>()
        if (alias == null) {
          recordError(argument.leftOperand, "Expected alias name")
        }
        else {
          alias.putUserData(ginqBinding, Unit)
          markAsReferenceResolveTarget(alias)
        }
        val dataSource = argument.rightOperand
        if (dataSource == null) {
          recordError(argument.operationToken, "Expected data source")
        } else {
          dataSource.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
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
      "on", "where", "having" -> {
        val argument = methodCall.getSingleArgument<GrExpression>()
        if (argument == null) {
          recordError(methodCall, "Expected a list of conditions")
          return
        } else {
          argument.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
        }
        if (callName == "on") {
          val last = container.lastOrNull()
          if (last is GinqJoinFragment && last.onCondition == null && argument is GrBinaryExpression) {
            val newJoin = last.copy(onCondition = GinqOnFragment(callKw, argument))
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
        else {
          val last = container.lastOrNull()
          if (last is GinqGroupByFragment && last.having == null) {
            val newGroupBy = last.copy(having = GinqHavingFragment(callKw, argument))
            container.removeLast()
            container.add(newGroupBy)
          }
        }
      }
      "groupby" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map {
          if (it is GrSafeCastExpression) AliasedExpression(it.operand, it.castTypeElement?.castSafelyTo<GrClassTypeElement>())
          else AliasedExpression(it, null)
        }
        if (arguments == null) {
          recordError(methodCall, "Expected a classifier argument")
          return
        }
        arguments.forEach {
          it.expression.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
        }
        container.add(GinqGroupByFragment(callKw, arguments, null))
      }
      "orderby" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map(::getOrdering)
        if (arguments == null) {
          recordError(methodCall, "Expected a list of order fields")
          return
        }
        arguments.forEach {
          it.sorter.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
        }
        container.add(GinqOrderByFragment(callKw, arguments))
      }
      "limit" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()
        if (arguments == null || arguments.isEmpty() || arguments.size > 2) {
          recordError(methodCall, "Expected one or two arguments for 'limit'")
          return
        }
        arguments.forEach { it.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit) }
        container.add(GinqLimitFragment(callKw, arguments[0], arguments.getOrNull(1)))
      }
      "select" -> {
        val distinct = methodCall.getSingleArgument<GrMethodCallExpression>()
          ?.takeIf { it.invokedExpression is GrReferenceExpression && (it.invokedExpression as GrReferenceExpression).referenceName == "distinct" }
        val arguments = (if (distinct != null) distinct.collectExpressionArguments() else methodCall.collectExpressionArguments<GrExpression>())
                        ?: return
        val parsedArguments = arguments.map { arg ->
          val deep = arg.skipParenthesesDownOrNull()
          val (aliased, alias) = if (deep is GrSafeCastExpression) deep.operand to deep.castTypeElement?.castSafelyTo<GrClassTypeElement>() else arg to null
          val windows = mutableListOf<GinqWindowFragment>()
          aliased.accept(object: GroovyRecursiveElementVisitor() {
            override fun visitMethodCallExpression(methodCallExpression: GrMethodCallExpression) {
              val invoked = methodCallExpression.invokedExpression.castSafelyTo<GrReferenceExpression>()?.takeIf { it.referenceName == "over" } ?: return super.visitMethodCallExpression(methodCallExpression)
              val qualifier = invoked.qualifierExpression ?: return super.visitMethodCallExpression(methodCallExpression)
              val argument = methodCallExpression.argumentList.allArguments.takeIf { it.size <= 1 } ?: return super.visitMethodCallExpression(methodCallExpression)
              val overKw = invoked.referenceNameElement ?: return super.visitMethodCallExpression(methodCallExpression)
              if (argument.isEmpty()) {
                windows.add(GinqWindowFragment(qualifier, overKw, null, emptyList(), null, null, emptyList()))
              } else {
                var partitionKw: PsiElement? = null
                var partitionArguments: List<GrExpression> = emptyList()
                var orderBy: GinqOrderByFragment? = null
                var rowsOrRangeKw: PsiElement? = null
                var rowsOrRangeArguments: List<GrExpression> = emptyList()
                var localQualifier = argument.single()
                while (localQualifier != null) {
                  val call = localQualifier.castSafelyTo<GrMethodCall>() ?: return super.visitMethodCallExpression(methodCallExpression)
                  val invokedInner = call.invokedExpression.castSafelyTo<GrReferenceExpression>() ?: return super.visitMethodCallExpression(methodCallExpression)
                  if (invokedInner.referenceName == "range" || invokedInner.referenceName == "rows") {
                    rowsOrRangeKw = invokedInner.referenceNameElement
                    rowsOrRangeArguments = call.argumentList.allArguments.toList().mapNotNull(GroovyPsiElement?::castSafelyTo)
                    rowsOrRangeArguments.forEach { it.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit) }
                    localQualifier = invokedInner.qualifier
                  } else if (invokedInner.referenceName == "partitionby") {
                    partitionKw = invokedInner.referenceNameElement
                    partitionArguments = call.argumentList.allArguments.toList().mapNotNull(GroovyPsiElement?::castSafelyTo)
                    partitionArguments.forEach { it.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit) }
                    localQualifier = invokedInner.qualifier
                  } else if (invokedInner.referenceName == "orderby") {
                    val orderByKw = invokedInner.referenceNameElement!!
                    val fields = call.argumentList.allArguments.toList().mapNotNull { it.castSafelyTo<GrExpression>()?.let(::getOrdering) }
                    orderBy = GinqOrderByFragment(orderByKw, fields)
                    orderBy.sortingFields.forEach { it.sorter.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit) }
                    localQualifier = invokedInner.qualifier
                  } else {
                    return super.visitMethodCallExpression(methodCallExpression)
                  }
                }
                windows.add(
                  GinqWindowFragment(qualifier, overKw, partitionKw, partitionArguments, orderBy, rowsOrRangeKw, rowsOrRangeArguments))
              }
              qualifier.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
              super.visitMethodCallExpression(methodCallExpression)
            }
          })
          AggregatableAliasedExpression(aliased, windows, alias)
        }
        parsedArguments.forEach {
          it.aggregatedExpression.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit)
          it.alias?.referenceElement?.let(::markAsReferenceResolveTarget)
          it.alias?.referenceElement?.putUserData(ginqBinding, Unit)
        }
        container.add(GinqSelectFragment(callKw, distinct?.invokedExpression?.castSafelyTo<GrReferenceExpression>(), parsedArguments))
      }
      "exists" -> { methodCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceNameElement?.putUserData(GinqMacroTransformationSupport.UNTRANSFORMED_ELEMENT, Unit) }
      else -> recordError(methodCall, "Unrecognized query")
    }
  }

  override fun visitExpression(expression: GrExpression) {
    if (isApproximatelyGinq(expression)) {
      val (innerErrors) =
        CachedValuesManager.getCachedValue(expression, rootGinq, CachedValueProvider { CachedValueProvider.Result(parseGinqAsExpr(expression), PsiModificationTracker.MODIFICATION_COUNT) })
      errors.addAll(innerErrors)
    }
    else {
      super.visitExpression(expression)
    }
  }
  private fun recordError(element: PsiElement, message: @Nls String) {
    errors.add(element to message)
  }

}

private inline fun <reified T : GrExpression> GrMethodCall.getSingleArgument(): T? =
  this.getArguments()?.singleOrNull()?.castSafelyTo<ExpressionArgument>()?.expression?.castSafelyTo<T>()

private inline fun <reified T : GrExpression> GrMethodCall.collectExpressionArguments(): List<T>? =
  this.getArguments()?.filterIsInstance<ExpressionArgument>()?.map { it.expression }?.filterIsInstance<T>()?.takeIf { it.size == getArguments()?.size }

private fun GrMethodCall.refCallIdentifier(): PsiElement = invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceNameElement
                                                           ?: invokedExpression

// e is not ginq --> false
private fun isApproximatelyGinq(e: PsiElement): Boolean {
  return e is GrMethodCall && e.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName == "select"
}

val rootGinq: Key<CachedValue<Pair<List<ParsingError>, GinqExpression?>>> = Key.create("root ginq expression")

@Deprecated("too internal, hide under functions")
val ginqBinding: Key<Unit> = Key.create("Ginq binding")

fun PsiElement.ginqParents(top: PsiElement, topExpr: GinqExpression): Sequence<GinqExpression> = sequence {
  for (parent in parents(true)) {
    if (parent == top) {
      yield(topExpr)
      return@sequence
    }
    val ginq = parent.getStoredGinq() ?: continue
    yield(ginq)
  }
}

typealias ParsingError = Pair<PsiElement, @Nls String>

val ginqKw = setOf("from", "where", "groupby", "having", "orderby", "limit", "on", "select") + joins

fun PsiElement.getStoredGinq() : GinqExpression? {
  return this.getUserData(rootGinq)?.upToDateOrNull?.get()?.second
}

fun getOrdering(expr: GrExpression): Ordering {
  if (expr is GrBinaryExpression && expr.operationTokenType == GroovyElementTypes.KW_IN) {
    val rightOperand = expr.rightOperand
    val (orderKw, nullsKw) = if (rightOperand is GrReferenceExpression) {
      rightOperand to null
    }
    else if (rightOperand is GrMethodCall && rightOperand.invokedExpression is GrReferenceExpression) {
      rightOperand.invokedExpression as GrReferenceExpression to
        rightOperand.argumentList.expressionArguments.singleOrNull()?.takeIf { it.text == "nullsfirst" || it.text == "nullslast" }
    }
    else null to null
    return when (orderKw?.referenceName) {
      "asc" -> Ordering.Asc(orderKw, nullsKw, expr.leftOperand)
      "desc" -> Ordering.Desc(orderKw, nullsKw, expr.leftOperand)
      else -> Ordering.Asc(null, null, expr)
    }
  }
  else {
    return Ordering.Asc(null, null, expr)
  }
}
