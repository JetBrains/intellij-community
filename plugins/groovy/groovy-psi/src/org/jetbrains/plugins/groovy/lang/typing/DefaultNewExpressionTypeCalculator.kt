// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrBuiltInTypeElement
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClassReferenceType

class DefaultNewExpressionTypeCalculator : GrTypeCalculator<GrNewExpression> {

  override fun getType(expression: GrNewExpression): PsiType? {
    return getAnonymousType(expression) ?:
           getRegularType(expression)
  }

  private fun getAnonymousType(expression: GrNewExpression): PsiType? {
    val anonymous = expression.anonymousClassDefinition ?: return null
    return JavaPsiFacade.getElementFactory(expression.project).createType(anonymous)
  }

  private fun getRegularType(expression: GrNewExpression): PsiType? {
    var type: PsiType = expression.referenceElement?.let { GrClassReferenceType(it) } ?:
                        (expression.typeElement as? GrBuiltInTypeElement)?.type ?:
                        return null
    repeat(expression.arrayCount) {
      type = type.createArrayType()
    }
    return type
  }
}
