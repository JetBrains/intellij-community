// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("FunctionalExpressionFlowUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.GrControlFlowOwner
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl.InvocationKind.*
import org.jetbrains.plugins.groovy.lang.psi.util.skipParenthesesDown
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

/**
 * Specifies how many times functional expression will be invoked.
 *
 * [EXACTLY_ONCE] is for functional expressions that will be invoked inplace and only one time.
 * Such functional expressions may be inlined in the place where they are defined.
 *
 * [ZERO_OR_MORE] is for functional expressions that will be invoked inplace, but amount of their invocations is undefined.
 * Such functional expressions act like code blocks under some conditional statement.
 *
 * [UNKNOWN] is for functional expressions for which we don't have any information:
 * neither if they are invoked inplace, nor about amount of invocations.
 */
enum class InvocationKind {
  EXACTLY_ONCE,
  ZERO_OR_MORE,
  UNKNOWN
}

private val trustedMethodsForExecutingOnce: Set<String> = setOf(
  "identity",
  "runAfter",
  "tap",
  "use",
  "with"
)

private val trustedMethodsForExecutingManyTimes: Set<String> = setOf(
  "any",
  "collect",
  "collectEntries",
  "collectMany",
  "collectNested",
  "combinations",
  "count",
  "countBy",
  "downto",
  "dropWhile",
  "each",
  "eachByte",
  "eachCombination",
  "eachPermutation",
  "eachWithIndex",
  "every",
  "find",
  "findAll",
  "findIndexOf",
  "findResult",
  "findResults",
  "flatten",
  "groupBy",
  "inject",
  "max",
  "min",
  "removeAll",
  "retainAll",
  "reverseEach",
  "sort",
  "split",
  "step",
  "sum",
  "takeWhile",
  "times",
  "toSorted",
  "toUnique",
  "unique",
  "upto"
)

private val knownMethods = trustedMethodsForExecutingManyTimes union trustedMethodsForExecutingOnce

fun GrFunctionalExpression?.getControlFlowOwner(): GrControlFlowOwner? = when (this) {
  is GrClosableBlock -> this
  is GrLambdaExpression -> body
  else -> null
}

fun computeInvocationKind(block: GrFunctionalExpression?): InvocationKind {
  val call = block?.parentOfType<GrMethodCall>()?.takeIf { call ->
    (call.invokedExpression as? GrReferenceExpression)?.referenceName in knownMethods &&
    call.getArguments()?.any { (it as? ExpressionArgument)?.expression?.skipParenthesesDown() === block } ?: false
  } ?: return UNKNOWN
  val method = call.multiResolve(false).firstOrNull()?.element as? GrGdkMethod
  val primaryInvocationKind = when (method?.name) {
    in trustedMethodsForExecutingOnce -> EXACTLY_ONCE
    in trustedMethodsForExecutingManyTimes -> ZERO_OR_MORE
    else -> return UNKNOWN
  }
  return primaryInvocationKind.weakenIfUsesSafeNavigation(call)
}

private fun InvocationKind.weakenIfUsesSafeNavigation(call: GrMethodCall): InvocationKind = when (this) {
  EXACTLY_ONCE -> {
    val refExpr = PsiTreeUtil.findChildOfType(call, GrReferenceExpression::class.java)
    if (refExpr != null && refExpr.dotToken?.text == "?.") {
      ZERO_OR_MORE
    }
    else {
      EXACTLY_ONCE
    }
  }
  else -> this
}


private const val GROOVY_PROCESS_NESTED_DFA = "groovy.process.nested.dfa"
internal fun isNestedFlowProcessingAllowed() : Boolean = Registry.`is`(GROOVY_PROCESS_NESTED_DFA, false)
