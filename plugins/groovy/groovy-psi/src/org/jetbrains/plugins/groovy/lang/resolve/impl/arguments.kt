// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.impl

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrSpreadArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType
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
