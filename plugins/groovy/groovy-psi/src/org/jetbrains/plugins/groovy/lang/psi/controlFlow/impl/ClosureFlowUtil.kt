// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ClosureFlowUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.util.skipParenthesesDown
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.impl.getArguments

enum class InvocationKind {
  EXACTLY_ONCE,
  INVOKED_INPLACE,
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

fun getInvocationKind(block: GrFunctionalExpression?): InvocationKind {
  val call = block?.parentOfType<GrMethodCall>()?.takeIf { call ->
    call.invokedExpression.lastChild.text in knownMethods &&
    call.getArguments()?.any { (it as? ExpressionArgument)?.expression?.skipParenthesesDown() === block } ?: false
  } ?: return InvocationKind.UNKNOWN
  val method = call.multiResolve(false).firstOrNull()?.element as? GrGdkMethod
  return when (method?.name) {
    in trustedMethodsForExecutingOnce -> InvocationKind.EXACTLY_ONCE
    in trustedMethodsForExecutingManyTimes -> InvocationKind.INVOKED_INPLACE
    else -> return InvocationKind.UNKNOWN
  }
}
