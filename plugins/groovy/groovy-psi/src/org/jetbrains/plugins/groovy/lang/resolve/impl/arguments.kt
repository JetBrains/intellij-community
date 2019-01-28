// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiSubstitutor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.psi.util.isEffectivelyVarArgs
import org.jetbrains.plugins.groovy.lang.psi.util.isOptional
import org.jetbrains.plugins.groovy.lang.resolve.api.*
import java.util.*

fun GrCall.getArguments(): Arguments? {
  val argumentList = argumentList ?: return null
  return getArguments(argumentList.namedArguments, argumentList.expressionArguments, closureArguments, this)
}

private fun getArguments(namedArguments: Array<out GrNamedArgument>,
                         expressionArguments: Array<out GrExpression>,
                         closureArguments: Array<out GrClosableBlock>,
                         context: PsiElement): Arguments? {
  val result = ArrayList<Argument>()
  if (namedArguments.isNotEmpty()) {
    result += LazyTypeArgument {
      GrMapType.createFromNamedArgs(context, namedArguments)
    }
  }

  for (expression in expressionArguments) {
    if (expression is GrSpreadArgument) {
      val type = expression.argument.type as? GrTupleType ?: return null
      type.componentTypes.mapTo(result) {
        JustTypeArgument(it)
      }
    }
    else {
      result += ExpressionArgument(expression)
    }
  }

  closureArguments.mapTo(result) {
    ExpressionArgument(it)
  }

  return result
}

fun argumentMapping(method: PsiMethod, substitutor: PsiSubstitutor, arguments: Arguments, context: PsiElement): ArgumentMapping = when {
  method.isEffectivelyVarArgs -> {
    val invokedAsIs = run(fun(): Boolean {
      // call foo(X[]) as is, i.e. with argument of type X[] (or subtype)
      val parameters = method.parameterList.parameters
      if (arguments.size != parameters.size) return false
      val parameterType = parameterType(parameters.last().type, substitutor, true)
      val lastArgApplicability = argumentApplicability(parameterType, arguments.last(), context)
      return lastArgApplicability == Applicability.applicable
    })
    if (invokedAsIs) {
      PositionalArgumentMapping(method, arguments, context)
    }
    else {
      VarargArgumentMapping(method, arguments, context)
    }
  }
  arguments.isEmpty() -> {
    val parameters = method.parameterList.parameters
    val parameter = parameters.singleOrNull()
    if (parameter != null && !parameter.isOptional && parameter.type is PsiClassType && !PsiUtil.isCompileStatic(context)) {
      NullArgumentMapping(parameter)
    }
    else {
      PositionalArgumentMapping(method, arguments, context)
    }
  }
  else -> PositionalArgumentMapping(method, arguments, context)
}
