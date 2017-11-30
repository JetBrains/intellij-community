// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getDelegatesToInfo

class GrClosureOwnerDelegateTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

  override fun getType(expression: GrReferenceExpression): PsiType? {
    val method = expression.resolve() as? PsiMethod ?: return null

    val methodName = method.name
    val delegate = "getDelegate" == methodName
    if (!delegate && "getOwner" != methodName) return null

    if (method.parameterList.parametersCount != 0) return null

    val closureClass = JavaPsiFacade.getInstance(expression.project).findClass(GROOVY_LANG_CLOSURE, expression.resolveScope)
    if (closureClass == null || closureClass != method.containingClass) return null

    val closure = PsiTreeUtil.getParentOfType(expression, GrClosableBlock::class.java) ?: return null
    return if (delegate) getDelegatesToInfo(closure)?.typeToDelegate else closure.ownerType
  }
}
