// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.closure

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE

internal object ImplicitClosureCallPredicate : PsiElementPredicate {

  override fun satisfiedBy(element: PsiElement): Boolean {
    if (element !is GrMethodCallExpression) {
      return false
    }
    if (ErrorUtil.containsError(element)) {
      return false
    }
    val result = element.advancedResolve()
    if (element.implicitCallReference == null) {
      return result.isInvokedOnProperty && element.invokedExpression.type.isClosureType()
    }
    else {
      return result.element.isClosureCallMethod()
    }
  }

  private fun PsiType?.isClosureType(): Boolean {
    return this != null && equalsToText(GROOVY_LANG_CLOSURE)
  }

  private fun PsiElement?.isClosureCallMethod(): Boolean {
    return this is PsiMethod && name == "call" && containingClass?.qualifiedName == GROOVY_LANG_CLOSURE
  }
}
