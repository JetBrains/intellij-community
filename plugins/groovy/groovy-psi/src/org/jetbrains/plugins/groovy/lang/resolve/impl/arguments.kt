// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
import org.jetbrains.plugins.groovy.lang.psi.util.isCompileStatic
import org.jetbrains.plugins.groovy.lang.resolve.api.*

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

  if (!getExpressionArguments(expressionArguments, result)) {
    return null
  }

  closureArguments.mapTo(result) {
    ExpressionArgument(it)
  }

  return result
}

private fun getExpressionArguments(expressionArguments: Array<out GrExpression>, result: MutableList<Argument>): Boolean {
  for (expression in expressionArguments) {
    if (expression is GrSpreadArgument) {
      val type = expression.argument.type as? GrTupleType ?: return false
      type.componentTypes.mapTo(result) {
        JustTypeArgument(it)
      }
    }
    else {
      result += ExpressionArgument(expression)
    }
  }
  return true
}

fun getExpressionArguments(expressionArguments: Array<out GrExpression>): Arguments? {
  val result = ArrayList<Argument>()
  if (getExpressionArguments(expressionArguments, result)) {
    return result
  }
  else {
    return null
  }
}

fun <X : CallParameter> argumentMapping(signature: CallSignature<X>, arguments: Arguments, context: PsiElement): ArgumentMapping<X> {
  val parameters = signature.parameters
  return when {
    signature.isVararg -> {
      val invokedAsIs = run(fun(): Boolean {
        // call foo(X[]) as is, i.e. with argument of type X[] (or subtype)
        if (arguments.size != parameters.size) return false
        val parameterType = TypeConversionUtil.erasure(parameters.last().type, PsiSubstitutor.EMPTY)
        val lastArgApplicability = argumentApplicability(parameterType, arguments.last().runtimeType, context)
        return lastArgApplicability == Applicability.applicable
      })
      if (invokedAsIs) {
        PositionalArgumentMapping(parameters, arguments, context)
      }
      else {
        VarargArgumentMapping(parameters, arguments, context)
      }
    }
    arguments.isEmpty() -> {
      val parameter = parameters.singleOrNull()
      if (parameter != null && !parameter.isOptional && parameter.type is PsiClassType && !isCompileStatic(context)) {
        NullArgumentMapping(parameter)
      }
      else {
        PositionalArgumentMapping(parameters, arguments, context)
      }
    }
    else -> {
      PositionalArgumentMapping(parameters, arguments, context)
    }
  }
}
