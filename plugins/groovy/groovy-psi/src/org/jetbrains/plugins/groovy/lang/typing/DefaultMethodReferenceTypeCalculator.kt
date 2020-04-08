// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class DefaultMethodReferenceTypeCalculator : GrTypeCalculator<GrReferenceExpression> {

  override fun getType(expression: GrReferenceExpression): PsiType? {
    if (!expression.hasMemberPointer()) {
      return null
    }
    return GroovyMethodReferenceType(expression)
  }
}
