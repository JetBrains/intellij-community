// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class ClosureMethodsCallTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (receiver !is GroovyClosureType) {
      return null
    }
    val methodName = method.name
    if (methodName !in interestingNames) {
      return null
    }
    if (method.containingClass?.qualifiedName != GROOVY_LANG_CLOSURE) return null

    if (methodName == "memoize") {
      return receiver
    }

    if (methodName == "call") {
      return receiver.returnType(arguments)
    }

    if (arguments == null) {
      return null
    }

    return when (methodName) {
      "rcurry" -> receiver.curry(-arguments.size, arguments, context)
      "curry",
      "trampoline" -> receiver.curry(0, arguments, context)
      "ncurry" -> {
        val literal = (arguments.firstOrNull() as? ExpressionArgument)?.expression as? GrLiteral
        val value = literal?.value as? Int
        if (value != null) {
          receiver.curry(value, arguments.drop(1), context)
        }
        else {
          receiver
        }
      }
      else -> null
    }
  }

  companion object {
    private val interestingNames = setOf(
      "call",
      "curry",
      "ncurry",
      "rcurry",
      "memoize",
      "trampoline"
    )
  }
}
