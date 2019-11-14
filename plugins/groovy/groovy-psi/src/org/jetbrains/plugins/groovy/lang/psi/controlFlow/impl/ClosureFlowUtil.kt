// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ClosureFlowUtil")

package org.jetbrains.plugins.groovy.lang.psi.controlFlow.impl

import com.intellij.psi.util.parentOfType
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil.skipParentheses

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

private val knownMethods = trustedMethodsForExecutingManyTimes union trustedMethodsForExecutingOnce

fun getInvocationKind(block: GrFunctionalExpression?): InvocationKind {
  block ?: return InvocationKind.UNKNOWN
  when (val statement = skipParentheses(block, true)?.parentOfType<GrStatement>()) {
    is GrMethodCall -> {
      if (statement.invokedExpression.lastChild.text !in knownMethods) {
        return InvocationKind.UNKNOWN
      }
      val resolvedStatement = statement.multiResolve(false).firstOrNull()?.element as? GrGdkMethod
      return when (resolvedStatement?.name) {
        in trustedMethodsForExecutingOnce -> InvocationKind.EXACTLY_ONCE
        in trustedMethodsForExecutingManyTimes -> InvocationKind.UNDETERMINED
        else -> return InvocationKind.UNKNOWN
      }

    }
    else -> return InvocationKind.UNKNOWN
  }
}
