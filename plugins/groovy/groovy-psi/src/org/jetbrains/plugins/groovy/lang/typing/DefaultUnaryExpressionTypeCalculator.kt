// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.CommonClassNames.JAVA_LANG_NUMBER
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrUnaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil

class DefaultUnaryExpressionTypeCalculator : GrTypeCalculator<GrUnaryExpression> {

  override fun getType(expression: GrUnaryExpression): PsiType? {
    val resolveResult = PsiImplUtil.extractUniqueResult(expression.reference.multiResolve(false))

    if (isIncDecNumber(resolveResult)) {
      return expression.operand?.type
    }

    val substituted = ResolveUtil.extractReturnTypeFromCandidate(resolveResult, expression, PsiType.EMPTY_ARRAY)
    if (substituted != null) {
      return substituted
    }

    val operand = expression.operand ?: return null
    val type = operand.type
    return if (TypesUtil.isNumericType(type)) type else null
  }

  //hack for DGM.next(Number):Number
  private fun isIncDecNumber(result: GroovyResolveResult): Boolean {
    val element = result.element as? PsiMethod ?: return false

    val method = (element as? GrGdkMethod)?.staticMethod ?: element

    val name = method.name
    if ("next" != name && "previous" != name) return false

    if (!PsiUtil.isDGMMethod(method)) return false

    val parameter = method.parameterList.parameters.singleOrNull() ?: return false
    return parameter.type.equalsToText(JAVA_LANG_NUMBER)
  }
}
