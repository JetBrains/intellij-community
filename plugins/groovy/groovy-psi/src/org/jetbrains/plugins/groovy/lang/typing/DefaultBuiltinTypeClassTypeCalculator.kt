// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBuiltinTypeClassExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createJavaLangClassType

class DefaultBuiltinTypeClassTypeCalculator : GrTypeCalculator<GrBuiltinTypeClassExpression> {

  override fun getType(expression: GrBuiltinTypeClassExpression): PsiType? {
    return createJavaLangClassType(expression.primitiveType, expression.project, expression.resolveScope)
  }
}
