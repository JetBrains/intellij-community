// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.intentions.closure

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.intentions.base.PsiElementPredicate
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.util.ErrorUtil

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

}
