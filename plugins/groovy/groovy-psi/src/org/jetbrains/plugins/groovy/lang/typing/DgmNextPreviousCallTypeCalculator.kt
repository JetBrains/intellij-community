// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.openapi.util.NlsSafe
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypeConstants.INTEGER_RANK
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypeConstants.getTypeRank
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.isNumericType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.binaryCalculators.GrBinaryExpressionUtil.getDefaultNumericResultType
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.DEFAULT_GROOVY_METHODS
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments

class DgmNextPreviousCallTypeCalculator : GrCallTypeCalculator {

  companion object {
    @NlsSafe
    private const val NEXT = "next"
    @NlsSafe
    private const val PREVIOUS = "previous"
  }

  override fun getType(receiver: PsiType?, method: PsiMethod, arguments: Arguments?, context: PsiElement): PsiType? {
    if (!isNextPrevious(method)) {
      return null
    }
    val type = arguments?.firstOrNull()?.type
    if (!isNumericType(type)) {
      return null
    }
    return getDefaultNumericResultType(getTypeRank(type), INTEGER_RANK, context)
  }

  private fun isNextPrevious(method: PsiMethod): Boolean {
    val name = method.name
    if (NEXT != name && PREVIOUS != name) {
      return false
    }
    if (method.containingClass?.qualifiedName != DEFAULT_GROOVY_METHODS) {
      return false
    }
    val parameter = method.parameterList.parameters.singleOrNull() ?: return false
    return parameter.type.equalsToText(CommonClassNames.JAVA_LANG_NUMBER)
  }
}
