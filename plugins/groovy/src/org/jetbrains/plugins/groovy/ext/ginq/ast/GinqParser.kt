// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.ext.ginq.ast

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValueProvider.Result
import com.intellij.psi.util.CachedValuesManager.getCachedValue
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.parents
import com.intellij.util.asSafely
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.ext.ginq.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.KW_IN
import org.jetbrains.plugins.groovy.lang.psi.GroovyRecursiveElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrClassTypeElement
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrStatementOwner
import org.jetbrains.plugins.groovy.lang.psi.util.skipParenthesesDownOrNull
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments
import org.jetbrains.plugins.groovy.lang.resolve.markAsReferenceResolveTarget

fun getTopParsedGinqTree(root: GinqRootPsiElement): GinqExpression? {
  return getTopParsedGinqInfo(root).second?.asSafely<GinqExpression>()
}

fun getTopShutdownGinq(root: GinqRootPsiElement): GinqShutdown? {
  return getTopParsedGinqInfo(root).second?.asSafely<GinqShutdown>()
}

fun PsiElement.getClosestGinqTree(root: GinqRootPsiElement): GinqExpression? {
  val top = getTopParsedGinqTree(root) ?: return null
  return ginqParents(root, top).firstOrNull()
}

fun getTopParsedGinqErrors(root: GinqRootPsiElement): List<ParsingError> {
  return getTopParsedGinqInfo(root).first
}

private fun getTopParsedGinqInfo(root: GinqRootPsiElement): Pair<List<ParsingError>, GenericGinqExpression?> {
  return getCachedValue(root.psi, INJECTED_GINQ_KEY, CachedValueProvider {
    Result(doGetTopParsedGinqInfo(root), PsiModificationTracker.MODIFICATION_COUNT)
  })
}

private fun doGetTopParsedGinqInfo(root: GinqRootPsiElement): Pair<List<ParsingError>, GenericGinqExpression?> {
  val owner = when (root) {
    is GinqRootPsiElement.Call -> {
      // macro case
      root.psi.expressionArguments.filterIsInstance<GrClosableBlock>().singleOrNull()
      ?: root.psi.closureArguments.singleOrNull()
      ?: return emptyList<ParsingError>() to null
    }
    is GinqRootPsiElement.Method -> {
      // method case
      root.psi.block ?: return emptyList<ParsingError>() to null
    }
  }
  val shutdown = getShutdown(owner)
  if (shutdown != null) return emptyList<ParsingError>() to shutdown
  return parseGinq(owner)
}

private fun getShutdown(owner: GrCodeBlock): GinqShutdown? {
  val statement = owner.statements.singleOrNull() ?: return null
  if (statement is GrReferenceExpression && statement.referenceNameElement?.text == KW_SHUTDOWN) {
    return GinqShutdown(statement.referenceNameElement!!, null)
  } else if (statement is GrMethodCall && statement.callRefName == KW_SHUTDOWN &&
             statement.expressionArguments.singleOrNull()?.text in listOf(KW_IMMEDIATE, KW_ABORT)) {
    return GinqShutdown(statement.refCallIdentifier(), statement.expressionArguments.single())
  }
  return null
}

private fun parseGinq(statementsOwner: GrStatementOwner): Pair<List<ParsingError>, GinqExpression?> {
  val parser = GinqParser()
  statementsOwner.statements.forEach { it.accept(parser) }
  return gatherGinqExpression(parser.errors, parser.incompleteFrom, parser.container)
}

private fun parseGinqAsExpr(psiGinq: GrExpression): Pair<List<ParsingError>, GinqExpression?> =
  GinqParser().also(psiGinq::accept).run { gatherGinqExpression(errors, incompleteFrom, container) }

private fun gatherGinqExpression(errors: MutableList<ParsingError>,
                                 incompleteFrom: Boolean,
                                 container: List<GinqQueryFragment>): Pair<List<ParsingError>, GinqExpression?> {
  if (container.isEmpty()) {
    return errors to null
  }
  val from = container.first() as? GinqFromFragment ?: return (errors + if (incompleteFrom) emptyList() else listOf (container.first().keyword to GroovyBundle.message("ginq.error.message.query.should.start.from.from"))) to null
  // otherwise it is a valid ginq expression
  val joins: MutableList<GinqJoinFragment> = mutableListOf()
  var where: GinqWhereFragment? = null
  var groupBy: GinqGroupByFragment? = null
  var orderBy: GinqOrderByFragment? = null
  var limit: GinqLimitFragment? = null
  var select: GinqSelectFragment? = null
  var index = 1
  while (index <= container.lastIndex) {
    when (val currentFragment = container[index]) {
      is GinqJoinFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(where?.keyword, groupBy?.keyword, orderBy?.keyword, limit?.keyword, select?.keyword).firstOrNull())
        if (currentFragment.onCondition == null && currentFragment.keyword.text != "crossjoin") {
          errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.on.is.expected.after.join"))
        }
        joins.add(container[index] as GinqJoinFragment)
      }
      is GinqWhereFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(groupBy?.keyword, orderBy?.keyword, limit?.keyword, select?.keyword).firstOrNull())
        where = currentFragment
      }
      is GinqGroupByFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(orderBy?.keyword, limit?.keyword, select?.keyword).firstOrNull())
        groupBy = currentFragment
      }
      is GinqOrderByFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(limit?.keyword, select?.keyword).firstOrNull())
        orderBy = currentFragment
      }
      is GinqLimitFragment -> {
        reportMisplacement(errors, currentFragment.keyword, listOfNotNull(select?.keyword).firstOrNull())
        limit = currentFragment
      }
      is GinqSelectFragment -> {
        select = currentFragment
      }
      is GinqFromFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.from.must.be.in.the.start.of.a.query"))
      is GinqHavingFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.0.must.be.after.1", "having", "groupby"))
      is GinqOnFragment -> errors.add(currentFragment.keyword to GroovyBundle.message("ginq.error.message.on.is.expected.after.join"))
    }
    index += 1
  }
  if (select == null) {
    errors.add(from.keyword to GroovyBundle.message("ginq.error.message.query.should.end.with.select"))
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
  var incompleteFrom = false

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
      // was parsed as a standalone ginq somewhere above
      return
    }
    val callName = methodCall.invokedExpression.asSafely<GrReferenceExpression>()?.referenceName
    if (callName == null) {
      return
    }
    val callKw = methodCall.refCallIdentifier()
    when (callName) {
      KW_FROM, in JOINS -> parseAsDataSource(methodCall, callName == KW_FROM, callKw)
      KW_ON, KW_WHERE, KW_HAVING -> parseAsFilteringFragment(methodCall, callName, callKw)
      KW_GROUPBY -> parseAsGroupBy(methodCall, callKw)
      KW_ORDERBY -> parseAsOrderBy(methodCall, callKw)
      KW_LIMIT -> parseAsLimit(methodCall, callKw)
      KW_SELECT -> parseAsSelect(methodCall, callKw)
      GINQ_EXISTS -> { /* it's fine to have exists as a top-level call */ }
      else -> recordError(methodCall.invokedExpression.asSafely<GrReferenceExpression>()?.referenceNameElement ?: methodCall.invokedExpression,
                          GroovyBundle.message("ginq.error.message.unrecognized.query"))
    }
  }

  fun parseAsDataSource(methodCall: GrMethodCall, isFrom: Boolean, callKw: PsiElement) {
    if (isFrom) {
      incompleteFrom = true
    }
    val argument = methodCall.getSingleArgument<GrBinaryExpression>()?.takeIf { it.operationTokenType == KW_IN }
    if (argument == null) {
      recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.expected.in.operator"))
      return
    }
    val alias = argument.leftOperand.asSafely<GrReferenceExpression>()
    if (alias == null) {
      recordError(argument.leftOperand, GroovyBundle.message("ginq.error.message.expected.alias"))
    }
    else {
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
    container.add(if (isFrom) GinqFromFragment(callKw, alias, dataSource) else GinqJoinFragment(callKw, alias, dataSource, null))
  }

  fun parseAsFilteringFragment(methodCall: GrMethodCall, callName: String, callKw: PsiElement) {
    val argument = methodCall.getSingleArgument<GrExpression>()
    if (argument == null) {
      recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.expected.a.boolean.expression"))
      return
    } else {
      argument.markAsGinqUntransformed()
    }
    if (callName == KW_ON) {
      val last = container.lastOrNull()
      if (last is GinqJoinFragment && last.onCondition == null) {
        if (last.keyword.text == KW_CROSSJOIN) {
          recordError(methodCall, GroovyBundle.message("ginq.error.message.on.should.not.be.provided.after.crossjoin"))
        }
        container.removeLast()
        container.add(last.copy(onCondition = GinqOnFragment(callKw, argument)))
      }
      else {
        recordError(methodCall, GroovyBundle.message("ginq.error.message.on.is.expected.after.join"))
      }
    }
    else if (callName == KW_WHERE) {
      container.add(GinqWhereFragment(callKw, argument))
    }
    else {
      val last = container.lastOrNull()
      if (last is GinqGroupByFragment && last.having == null) {
        container.removeLast()
        container.add(last.copy(having = GinqHavingFragment(callKw, argument)))
      }
    }
  }

  private fun parseAsGroupBy(methodCall: GrMethodCall, callKw: PsiElement) {
    val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map {
      if (it is GrSafeCastExpression) AliasedExpression(it.operand, it.castTypeElement?.asSafely<GrClassTypeElement>())
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

  private fun parseAsOrderBy(methodCall: GrMethodCall, callKw: PsiElement) {
    val arguments = methodCall.collectExpressionArguments<GrExpression>()?.map(::getOrdering)
    if (arguments == null) {
      recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.orderby.expected.a.list.of.ordering.fields"))
      return
    }
    arguments.forEach { it.sorter.markAsGinqUntransformed() }
    container.add(GinqOrderByFragment(callKw, arguments))
  }

  private fun parseAsLimit(methodCall: GrMethodCall, callKw: PsiElement) {
    val arguments = methodCall.collectExpressionArguments<GrExpression>()
    if (arguments == null || arguments.isEmpty() || arguments.size > 2) {
      recordError(methodCall.argumentList, GroovyBundle.message("ginq.error.message.expected.one.or.two.arguments.for.limit"))
      return
    }
    arguments.forEach(GrExpression::markAsGinqUntransformed)
    container.add(GinqLimitFragment(callKw, arguments[0], arguments.getOrNull(1)))
  }

  private fun parseAsSelect(methodCall: GrMethodCall, callKw: PsiElement) {
    val distinct = methodCall.getSingleArgument<GrMethodCallExpression>()
      ?.takeIf { it.invokedExpression is GrReferenceExpression && (it.invokedExpression as GrReferenceExpression).referenceName == "distinct" }
    val arguments = (if (distinct != null) distinct.collectExpressionArguments() else methodCall.collectExpressionArguments<GrExpression>())
                    ?: return
    val parsedArguments = arguments.map { arg ->
      val deep = arg.skipParenthesesDownOrNull()
      val (aliased, alias) = if (deep is GrSafeCastExpression) deep.operand to deep.castTypeElement?.asSafely<GrClassTypeElement>() else arg to null
      val windows = GinqWindowCollector().also { aliased.accept(it) }.windows
      AggregatableAliasedExpression(aliased, windows, alias)
    }
    parsedArguments.forEach {
      it.aggregatedExpression.markAsGinqUntransformed()
      it.alias?.referenceElement?.let(::markAsReferenceResolveTarget)
    }
    container.add(GinqSelectFragment(callKw, distinct?.invokedExpression?.asSafely<GrReferenceExpression>(), parsedArguments))
  }

  override fun visitExpression(expression: GrExpression) {
    if (!isTopLevel && isApproximatelyGinq(expression)) {
      val (innerErrors) =
        getCachedValue(expression, INJECTED_GINQ_KEY,
                       CachedValueProvider { Result(parseGinqAsExpr(expression), PsiModificationTracker.MODIFICATION_COUNT) })
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
  this.getArguments()?.singleOrNull()?.asSafely<ExpressionArgument>()?.expression?.asSafely<T>()

private inline fun <reified T : GrExpression> GrMethodCall.collectExpressionArguments(): List<T>? =
  this.getArguments()?.filterIsInstance<ExpressionArgument>()?.map { it.expression }?.filterIsInstance<T>()?.takeIf { it.size == getArguments()?.size }

internal fun GrMethodCall.refCallIdentifier(): PsiElement = invokedExpression.asSafely<GrReferenceExpression>()?.referenceNameElement
                                                           ?: invokedExpression
internal val GrMethodCall.callRefName: String? get() = invokedExpression.asSafely<GrReferenceExpression>()?.referenceName

private fun isApproximatelyGinq(e: PsiElement): Boolean {
  val available = listOf("select", "from")
  val qualifiers = generateSequence({ e }) {
    if (it is GrMethodCall) it.invokedExpression else if (it is GrReferenceExpression) it.qualifierExpression else null
  }
  return qualifiers.any { (it is GrMethodCall && it.invokedExpression.asSafely<GrReferenceExpression>()?.referenceName in available) || (it is GrReferenceExpression && it.referenceName in available)}
}

private val INJECTED_GINQ_KEY: Key<CachedValue<Pair<List<ParsingError>, GenericGinqExpression?>>> = Key.create("root ginq expression")

internal fun PsiElement.isGinqRoot() : Boolean = getUserData(INJECTED_GINQ_KEY) != null

internal fun PsiElement.getStoredGinq() : GinqExpression? = this.getUserData(INJECTED_GINQ_KEY)?.upToDateOrNull?.get()?.second?.asSafely<GinqExpression>()

private val GINQ_UNTRANSFORMED_ELEMENT: Key<Unit> = Key.create("Untransformed psi element within Groovy macro")

internal fun PsiElement.markAsGinqUntransformed() = putUserData(GINQ_UNTRANSFORMED_ELEMENT, Unit)

internal fun PsiElement.isGinqUntransformed() = getUserData(GINQ_UNTRANSFORMED_ELEMENT) != null

fun PsiElement.ginqParents(root: GinqRootPsiElement, topExpr: GinqExpression): Sequence<GinqExpression> = sequence {
  for (parent in parents(true)) {
    if (parent == root) {
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
        rightOperand.argumentList.expressionArguments.singleOrNull()?.takeIf { it.text == KW_NULLSFIRST || it.text == KW_NULLSLAST }
    }
    else null to null
    return when (orderKw?.referenceName) {
      KW_ASC -> Ordering.Asc(orderKw, nullsKw, expr.leftOperand)
      KW_DESC -> Ordering.Desc(orderKw, nullsKw, expr.leftOperand)
      else -> Ordering.Asc(null, null, expr.leftOperand)
    }
  }
  else {
    return Ordering.Asc(null, null, expr)
  }
}
