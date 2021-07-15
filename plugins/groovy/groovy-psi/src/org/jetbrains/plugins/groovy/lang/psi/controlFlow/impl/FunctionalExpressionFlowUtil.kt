// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("FunctionalExpressionFlowUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.NonNls
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

enum class InvocationKind {
  /**
   * Indicates that functional expressions will be invoked inplace and only one time.
   * Such functional expressions may be inlined in the place where they are defined.
   */
  IN_PLACE_ONCE,

  /**
   * Indicates that functional expressions will be invoked inplace, but amount of their invocations is undefined.
   * Such functional expressions act like code blocks under some conditional statement.
   */
  IN_PLACE_UNKNOWN,

  /**
   * Indicates that functional expressions does not provide any information:
   * neither if it is invoked inplace, nor about the amount of invocations.
   */
  UNKNOWN
}

@NonNls
private val trustedMethodsForExecutingOnce: Set<String> = setOf(
  "identity",
  "runAfter",
  "tap",
  "use",
  "with"
)

@NonNls
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

fun GrFunctionalExpression?.getInvocationKind(): InvocationKind {
  if (this == null) {
    return UNKNOWN
  }
  return CachedValuesManager.getCachedValue(this) {
    CachedValueProvider.Result(computeInvocationKind(this), this)
  }
}

private fun computeInvocationKind(block: GrFunctionalExpression): InvocationKind {
  val call = block.parentOfType<GrMethodCall>() ?: return UNKNOWN
  if ((call.invokedExpression as? GrReferenceExpression)?.referenceName !in knownMethods) {
    return UNKNOWN
  }
  if (call.getArguments()?.none { (it as? ExpressionArgument)?.expression?.skipParenthesesDown() === block } == true) {
    return UNKNOWN
  }
  val method = call.multiResolve(false).firstOrNull()?.element as? GrGdkMethod
  val primaryInvocationKind = when (method?.name) {
    in trustedMethodsForExecutingOnce -> IN_PLACE_ONCE
    in trustedMethodsForExecutingManyTimes -> IN_PLACE_UNKNOWN
    else -> return UNKNOWN
  }
  return primaryInvocationKind.weakenIfUsesSafeNavigation(call)
}

private fun InvocationKind.weakenIfUsesSafeNavigation(call: GrMethodCall): InvocationKind = when (this) {
  IN_PLACE_ONCE -> {
    val refExpr = PsiTreeUtil.findChildOfType(call, GrReferenceExpression::class.java)
    if (refExpr != null && refExpr.dotToken?.text == "?.") {
      IN_PLACE_UNKNOWN
    }
    else {
      IN_PLACE_ONCE
    }
  }
  else -> this
}


internal const val GROOVY_PROCESS_NESTED_DFA = "groovy.process.nested.dfa"
internal fun isNestedFlowProcessingAllowed(): Boolean = Registry.`is`(GROOVY_PROCESS_NESTED_DFA, false)
