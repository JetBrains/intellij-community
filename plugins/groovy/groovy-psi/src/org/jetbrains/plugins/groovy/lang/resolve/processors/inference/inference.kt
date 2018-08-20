// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors.inference

import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.util.ArrayUtil
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstant
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import java.util.*

fun getTopLevelType(expression: GrExpression): PsiType? {
  if (expression is GrMethodCall) {
    val resolved = expression.advancedResolve()
    (resolved as? GroovyMethodResult)?.candidate?.let {
      val session = GroovyInferenceSessionBuilder(expression.invokedExpression as GrReferenceExpression, it).build()
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
      } else {
        type
      }
    }
  }.toTypedArray()
}