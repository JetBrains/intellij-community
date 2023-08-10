// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.GrRangeExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GrRangeType

class DefaultRangeTypeCalculator : GrTypeCalculator<GrRangeExpression> {

  override fun getType(expression: GrRangeExpression): PsiType {
    val ltype = expression.from.type
    val rtype = expression.to?.type
    return GrRangeType(expression.resolveScope, JavaPsiFacade.getInstance(expression.project), ltype, rtype)
  }
}
