// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.util.containers.toArray
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType
import org.jetbrains.plugins.groovy.lang.psi.impl.signatures.GrClosureSignatureUtil.getReturnType
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument

class ClosureMethodsCallTypeCalculator : GrCallTypeCalculator {

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (receiver !is GrClosureType) return null
    val methodName = method.name
    if (methodName !in interestingNames) return null
    if (method.containingClass?.qualifiedName != GROOVY_LANG_CLOSURE) return null

    if (methodName == "memoize") {
      return receiver
    }

    val argumentTypes = arguments?.map(Argument::type)?.toArray(PsiType.EMPTY_ARRAY)

    if (methodName == "call") {
      return getReturnType(receiver.signatures, argumentTypes, context)
    }

    if (arguments == null || argumentTypes == null) {
      return null
    }

    return when (methodName) {
      "rcurry" -> receiver.curry(argumentTypes, -1, context)
      "curry",
      "trampoline" -> receiver.curry(argumentTypes, 0, context)
      "ncurry" -> {
        val literal = (arguments.firstOrNull() as? ExpressionArgument)?.expression as? GrLiteral
        val value = literal?.value as? Int
        if (value != null) {
          receiver.curry(argumentTypes.drop(1).toArray(PsiType.EMPTY_ARRAY), value, context)
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
