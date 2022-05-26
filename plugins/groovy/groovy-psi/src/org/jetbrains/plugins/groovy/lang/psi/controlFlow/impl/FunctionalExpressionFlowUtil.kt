// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

/**
 * Invocation kinds for closures are __temporarily__ disabled.
 *
 * At first, I tried to solve the problem of handling side effects of functional expressions, such as
 * ```
 * if (a instanceof A) {
 *    with(1) {
 *      // here a has type A
 *    }
 * }
 * ```
 * Not every closure should be inlined in that way, so it required kotlin-like distinction of types for closure invocation.
 * That is why this class was created. Unfortunately, it has some drawbacks:
 * it required enormous amount of computation power to determine the kind of closure, like resolve of the method, invoking the closure,
 * and some auxiliary precomputations before actually running type inference. Also, increased number of dependencies in control flow graph
 * implied more time spent in thread-local resolves.
 * It must be noted that most of the closures are actually [UNKNOWN] or [IN_PLACE_UNKNOWN], because amount of [IN_PLACE_ONCE] closures is very low.
 * Users do not have the ability to specify the kind of closure execution, and, considering groovy popularity, it's unlikely to appear.
 * Therefore, it makes little sense in [IN_PLACE_ONCE].
 *
 * [UNKNOWN], on the other hand, is likely to be the most popular invocation kind. But correct handling of it is too complicated: we need to
 * track all side effects, happening in the unknown closure, and considering them at __every__ usage of side-effect-affected object.
 *
 * So at last I decided to remove the effects of [UNKNOWN] and [IN_PLACE_ONCE] in favor of [IN_PLACE_UNKNOWN], because its handling is relatively easy.
 * I do not completely lose my hope to distinguish different closures and reach the primary goal specified above, but the
 * low number of code parts that can benefit from these changes (in their current implementation) do not worth performance degradation.
 */
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

/**
 * Most of the invocations should be with mayCache=true
 */
fun GrFunctionalExpression?.getInvocationKind(mayCache: Boolean = true): InvocationKind {
  if (this == null) {
    return UNKNOWN
  }
  if (mayCache) {
    return CachedValuesManager.getCachedValue(this) {
      CachedValueProvider.Result(computeInvocationKind(this), this)
    }
  } else {
    return computeInvocationKind(this)
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


private const val GROOVY_FLAT_DFA = "groovy.flat.dfa"
internal fun isFlatDFAAllowed(): Boolean = Registry.`is`(GROOVY_FLAT_DFA, false)
