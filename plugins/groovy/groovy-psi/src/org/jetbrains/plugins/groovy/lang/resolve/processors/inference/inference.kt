// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil.extractIterableTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getQualifierType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.api.Argument
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.JustTypeArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.UnknownArgument
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint

fun getTopLevelType(expression: GrExpression): PsiType? {
  if (expression is GrMethodCall) {
    val resolved = expression.advancedResolve() as? GroovyMethodResult
    resolved?.candidate?.let {
      val session = GroovyInferenceSessionBuilder(expression, it, resolved.contextSubstitutor)
        .resolveMode(false)
        .build()
      return session.inferSubst().substitute(PsiUtil.getSmartReturnType(it.method))
    }
    return null
  }

  if (expression is GrClosableBlock) {
    return TypesUtil.createTypeByFQClassName(GroovyCommonClassNames.GROOVY_LANG_CLOSURE, expression)
  }

  return expression.type
}

fun getTopLevelTypeCached(expression: GrExpression): PsiType? {
  return GroovyPsiManager.getInstance(expression.project).getTopLevelType(expression)
}

fun buildQualifier(ref: GrReferenceExpression, state: ResolveState): Argument {
  val qualifierExpression = ref.qualifierExpression
  val spreadState = state[SpreadState.SPREAD_STATE]
  if (qualifierExpression != null && spreadState == null) {
    return ExpressionArgument(qualifierExpression)
  }

  val resolvedThis = state[ClassHint.THIS_TYPE]
  if (resolvedThis != null) {
    return JustTypeArgument(resolvedThis)
  }

  val type = getQualifierType(ref)
  when {
    spreadState == null -> return JustTypeArgument(type)
    type == null -> return UnknownArgument
    else -> return JustTypeArgument(extractIterableTypeParameter(type, false))
  }
}

fun PsiSubstitutor.putAll(parameters: Array<out PsiTypeParameter>, arguments: Array<out PsiType>): PsiSubstitutor {
  if (arguments.size != parameters.size) return this
  return parameters.zip(arguments).fold(this) { acc, (param, arg) ->
    acc.put(param, arg)
  }
}

fun PsiClass.type(): PsiClassType {
  return PsiElementFactory.SERVICE.getInstance(project).createType(this, PsiSubstitutor.EMPTY)
}
