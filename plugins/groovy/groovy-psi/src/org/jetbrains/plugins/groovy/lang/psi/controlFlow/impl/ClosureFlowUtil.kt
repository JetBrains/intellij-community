// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ClosureFlowUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import org.jetbrains.plugins.groovy.codeInspection.utils.ControlFlowUtils
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod

enum class InvocationKind {
  EXACTLY_ONCE,
  UNDETERMINED,
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

fun getInvocationKind(block: GrFunctionalExpression?): InvocationKind {
  block ?: return InvocationKind.UNKNOWN
  when (val statement = ControlFlowUtils.getContainingNonTrivialStatement(block)) {
    is GrMethodCall -> {
      val resolvedStatement = statement.multiResolve(false).firstOrNull()?.element as? GrGdkMethod
      return when {
        resolvedStatement == null -> InvocationKind.UNKNOWN
        resolvedStatement.name in trustedMethodsForExecutingOnce -> InvocationKind.EXACTLY_ONCE
        resolvedStatement.name in trustedMethodsForExecutingManyTimes -> InvocationKind.UNDETERMINED
        else -> return InvocationKind.UNKNOWN
      }
    }
    else -> return InvocationKind.UNKNOWN
  }
}
