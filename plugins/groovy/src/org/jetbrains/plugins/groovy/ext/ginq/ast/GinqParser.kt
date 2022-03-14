// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import com.intellij.util.castSafelyTo
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.ext.ginq.joins
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_IN
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
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

private fun gatherGinqExpression(errors: MutableList<ParsingError>,
                                 container: List<GinqQueryFragment>): Pair<List<ParsingError>, GinqExpression?> {
  if (container.isEmpty()) {
    return emptyList<ParsingError>() to null
  }
  val from = container.first() as? GinqFromFragment ?: return listOf(container.first().keyword to GroovyBundle.message("ginq.error.message.query.should.start.from.from")) to null
  val select = container.last() as? GinqSelectFragment ?: return listOf((container.last().keyword to GroovyBundle.message("ginq.error.message.query.should.end.with.select"))) to null
  // otherwise it is a valid ginq expression
  val joins: MutableList<GinqJoinFragment> = mutableListOf()
  var where: GinqWhereFragment? = null
  var groupBy: GinqGroupByFragment? = null
  var orderBy: GinqOrderByFragment? = null
  var limit: GinqLimitFragment? = null
  var index = 1
  while (index < container.lastIndex) {
    when (val currentFragment = container[index]) {
      is GinqJoinFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(where?.keyword, groupBy?.keyword, orderBy?.keyword, limit?.keyword).firstOrNull())
        joins.add(container[index] as GinqJoinFragment)
      }
      is GinqWhereFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(groupBy?.keyword, orderBy?.keyword, limit?.keyword).firstOrNull())
        where = currentFragment
      }
      is GinqGroupByFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(orderBy?.keyword, limit?.keyword).firstOrNull())
        groupBy = currentFragment
      }
      is GinqOrderByFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(limit?.keyword).firstOrNull())
        orderBy = currentFragment
      }
      is GinqLimitFragment -> {
        limit = currentFragment
      }
      is GinqFromFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.from.must.be.in.the.start.of.a.query"))
      is GinqHavingFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.0.must.be.after.1", "having", "groupby"))
      is GinqOnFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.on.is.expected.after.join"))
      is GinqSelectFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.select.must.be.in.the.end.of.a.query"))
    }
    index += 1
  }
  return errors to GinqExpression(from, joins, where, groupBy, orderBy, limit, select)
}

private fun reportMisplacement(errors: MutableList<ParsingError>, kwBefore: PsiElement, kwAfter: PsiElement?) {
  if (kwAfter == null) {
    return
  }
  errors.add(kwBefore to GroovyBundle.message("ginq.error.message.0.must.be.before.1", kwBefore.text, kwAfter.text))
  errors.add(kwAfter to GroovyBundle.message("ginq.error.message.0.must.be.after.1", kwAfter.text, kwBefore.text))
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
    if (methodCall.getStoredGinq() != null) {
      // was parsed as standalone ginq somewhere above
      return
    }
    val callName = methodCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceName
    if (callName == null) {
      return
    }
    val callKw = methodCall.refCallIdentifier()
    when (callName) {
      "from", in joins -> {
        val argument = methodCall.getSingleArgument<GrBinaryExpression>()?.takeIf { it.operationTokenType == KW_IN }
        if (argument == null) {
          recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.expected.in.operator"))
          return
        }
        val alias = argument.leftOperand.castSafelyTo<GrReferenceExpression>()
        if (alias == null) {
          recordError(argument.leftOperand, GroovyBundle.message("ginq.error.message.expected.alias"))
        }
        else {
          alias.putUserData(GINQ_BINDING, Unit)
          markAsReferenceResolveTarget(alias)
        }
        val dataSource = argument.rightOperand
        if (dataSource == null) {
          recordError(argument.operationToken, GroovyBundle.message("ginq.error.message.expected.data.source"))
        } else {
          dataSource.markAsGinqUntransformed()
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
          recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.expected.a.boolean.expression"))
          return
        } else {
          argument.markAsGinqUntransformed()
        }
        if (callName == "on") {
          val last = container.lastOrNull()
          if (last is GinqJoinFragment && last.onCondition == null && argument is GrBinaryExpression) {
            val newJoin = last.copy(onCondition = GinqOnFragment(callKw, argument))
            container.removeLast()
            container.add(newJoin)
          }
          else {
            recordError(methodCall, GroovyBundle.message("ginq.error.message.on.is.expected.after.join"))
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
          recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.groupby.expected.a.list.of.expressions"))
          return
        }
        arguments.forEach {
          it.expression.markAsGinqUntransformed()
        }
        container.add(GinqGroupByFragment(callKw, arguments, null))
      }
      "orderby" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map(::getOrdering)
        if (arguments == null) {
          recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.orderby.expected.a.list.of.ordering.fields"))
          return
        }
        arguments.forEach {
          it.sorter.markAsGinqUntransformed()
        }
        container.add(GinqOrderByFragment(callKw, arguments))
      }
      "limit" -> {
        val arguments = methodCall.collectExpressionArguments<GrExpression>()
        if (arguments == null || arguments.isEmpty() || arguments.size > 2) {
          recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.expected.one.or.two.arguments.for.limit"))
          return
        }
        arguments.forEach(GrExpression::markAsGinqUntransformed)
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
                  when (invokedInner.referenceName) {
                    "range", "rows" -> {
                      rowsOrRangeKw = invokedInner.referenceNameElement
                      rowsOrRangeArguments = call.argumentList.allArguments.toList().mapNotNull(GroovyPsiElement?::castSafelyTo)
                      rowsOrRangeArguments.forEach { it.markAsGinqUntransformed() }
                      localQualifier = invokedInner.qualifier
                    }
                    "partitionby" -> {
                      partitionKw = invokedInner.referenceNameElement
                      partitionArguments = call.argumentList.allArguments.toList().mapNotNull(GroovyPsiElement?::castSafelyTo)
                      partitionArguments.forEach { it.markAsGinqUntransformed() }
                      localQualifier = invokedInner.qualifier
                    }
                    "orderby" -> {
                      val orderByKw = invokedInner.referenceNameElement!!
                      val fields = call.argumentList.allArguments.toList().mapNotNull { it.castSafelyTo<GrExpression>()?.let(::getOrdering) }
                      orderBy = GinqOrderByFragment(orderByKw, fields)
                      orderBy.sortingFields.forEach { it.sorter.markAsGinqUntransformed() }
                      localQualifier = invokedInner.qualifier
                    }
                    else -> {
                      return super.visitMethodCallExpression(methodCallExpression)
                    }
                  }
                }
                windows.add(
                  GinqWindowFragment(qualifier, overKw, partitionKw, partitionArguments, orderBy, rowsOrRangeKw, rowsOrRangeArguments))
              }
              qualifier.markAsGinqUntransformed()
              super.visitMethodCallExpression(methodCallExpression)
            }
          })
          AggregatableAliasedExpression(aliased, windows, alias)
        }
        parsedArguments.forEach {
          it.aggregatedExpression.markAsGinqUntransformed()
          it.alias?.referenceElement?.let(::markAsReferenceResolveTarget)
          it.alias?.referenceElement?.putUserData(GINQ_BINDING, Unit)
        }
        container.add(GinqSelectFragment(callKw, distinct?.invokedExpression?.castSafelyTo<GrReferenceExpression>(), parsedArguments))
      }
      "exists" -> { methodCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceNameElement?.markAsGinqUntransformed() }
      else -> recordError(methodCall.invokedExpression.castSafelyTo<GrReferenceExpression>()?.referenceNameElement ?: methodCall.invokedExpression,
                          GroovyBundle.message("ginq.error.message.unrecognized.query"))
    }
  }

  override fun visitExpression(expression: GrExpression) {
    if (isApproximatelyGinq(expression)) {
      val (innerErrors) =
        CachedValuesManager.getCachedValue(expression, INJECTED_GINQ_KEY, CachedValueProvider { CachedValueProvider.Result(parseGinqAsExpr(expression), PsiModificationTracker.MODIFICATION_COUNT) })
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

private val INJECTED_GINQ_KEY: Key<CachedValue<Pair<List<ParsingError>, GinqExpression?>>> = Key.create("root ginq expression")

fun PsiElement.isGinqRoot() : Boolean = getUserData(INJECTED_GINQ_KEY) != null

fun PsiElement.getStoredGinq() : GinqExpression? = this.getUserData(INJECTED_GINQ_KEY)?.upToDateOrNull?.get()?.second

private val GINQ_BINDING: Key<Unit> = Key.create("Ginq binding")

fun PsiElement.isGinqBinding() : Boolean = getUserData(GINQ_BINDING) != null

private val GINQ_UNTRANSFORMED_ELEMENT: Key<Unit> = Key.create("Untransformed psi element within Groovy macro")

fun PsiElement.markAsGinqUntransformed() = putUserData(GINQ_UNTRANSFORMED_ELEMENT, Unit)

fun PsiElement.isGinqUntransformed() = getUserData(GINQ_UNTRANSFORMED_ELEMENT) != null

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

fun getOrdering(expr: GrExpression): Ordering {
  if (expr is GrBinaryExpression && expr.operationTokenType == KW_IN) {
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

fun getParsedGinqTree(macroCall: GrCall): GinqExpression? {
  return getParsedGinqInfo(macroCall).second
}

fun getParsedGinqErrors(macroCall: GrCall): List<ParsingError> {
  return getParsedGinqInfo(macroCall).first
}

private fun getParsedGinqInfo(macroCall: GrCall): Pair<List<ParsingError>, GinqExpression?> {
  return CachedValuesManager.getCachedValue(macroCall, INJECTED_GINQ_KEY, CachedValueProvider {
    CachedValueProvider.Result(doGetParsedGinqTree(macroCall), PsiModificationTracker.MODIFICATION_COUNT)
  })
}

private fun doGetParsedGinqTree(macroCall: GrCall): Pair<List<ParsingError>, GinqExpression?> {
  val closure = macroCall.expressionArguments.filterIsInstance<GrClosableBlock>().singleOrNull()
                ?: macroCall.closureArguments.singleOrNull()
                ?: return emptyList<ParsingError>() to null
  return parseGinq(closure)
}
