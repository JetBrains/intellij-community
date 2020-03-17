// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.typing

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty
import org.jetbrains.plugins.groovy.lang.psi.util.getArrayClassType
import org.jetbrains.plugins.groovy.lang.psi.util.getSimpleArrayAccessType

class DefaultIndexAccessTypeCalculator : GrTypeCalculator<GrIndexProperty> {

  override fun getType(expression: GrIndexProperty): PsiType? {
    return expression.getArrayClassType()
           ?: expression.getSimpleArrayAccessType()
           ?: fromGetAtCall(expression)
  }

  private fun fromGetAtCall(expression: GrIndexProperty): PsiType? {
    val reference = expression.rValueReference ?: return null
    return getTypeFromResult(reference.advancedResolve(), reference.arguments, expression)
  }
}
