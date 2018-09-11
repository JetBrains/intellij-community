// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil.extractIterableTypeParameter
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.SpreadState
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil.getQualifierType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint
import java.util.*

fun getTopLevelType(expression: GrExpression): PsiType? {
  if (expression is GrMethodCall) {
    val resolved = expression.advancedResolve()
    (resolved as? GroovyMethodResult)?.candidate?.let {
      val session = GroovyInferenceSessionBuilder(expression.invokedExpression as GrReferenceExpression, it)
        .resolveMode(false)
        .build()
      return session.inferSubst().substitute(PsiUtil.getSmartReturnType(it.method))
    }
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
    return Argument(null, qualifierExpression)
  }

  val resolvedThis = state[ClassHint.THIS_TYPE]
  if (resolvedThis != null) {
    return Argument(resolvedThis, null)
  }

  val type = getQualifierType(ref)
  when {
    spreadState == null -> return Argument(type, null)
    type == null -> return Argument(null, null)
    else -> return Argument(extractIterableTypeParameter(type, false), null)
  }
}

fun buildArguments(place: PsiElement): List<Argument> {
  val parent = place as? GrEnumConstant ?: place.parent
  if (parent is GrCall) {
    val result = ArrayList<Argument>()
    val namedArgs = parent.namedArguments
    val expressions = parent.expressionArguments
    val closures = parent.closureArguments

    if (namedArgs.isNotEmpty()) {
      val context = namedArgs[0]
      result.add(Argument(GrMapType.createFromNamedArgs(context, namedArgs), null))
    }

    val argExp = ArrayUtil.mergeArrays(expressions, closures)
    result.addAll(argExp.map { exp -> Argument(null, exp) })
    return result
  }

  val argumentTypes = PsiUtil.getArgumentTypes(place, false, null) ?: return emptyList()
  return argumentTypes.map { t -> Argument(t, null) }
}


fun buildTopLevelArgumentTypes(place: PsiElement): Array<PsiType?> {
  return buildArguments(place).map { (type, expression) ->
    if (expression != null) {
      getTopLevelTypeCached(expression)
    }
    else {
      if (type is GrMapType) {
        TypesUtil.createTypeByFQClassName(CommonClassNames.JAVA_UTIL_MAP, place)
      }
      else {
        type
      }
    }
  }.toTypedArray()
}

fun PsiSubstitutor.putAll(parameters: Array<out PsiTypeParameter>, arguments: Array<out PsiType>): PsiSubstitutor {
  if (arguments.size != parameters.size) return this
  return parameters.zip(arguments).fold(this) { acc, (param, arg) ->
    acc.put(param, arg)
  }
}
