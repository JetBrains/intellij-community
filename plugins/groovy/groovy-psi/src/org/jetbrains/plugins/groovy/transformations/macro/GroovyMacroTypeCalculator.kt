// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.macro

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator

class GroovyMacroTypeCalculator: GrTypeCalculator<GrMethodCallExpression> {

  override fun getType(expression: GrMethodCallExpression): PsiType? {
    return getAvailableMacroSupport(expression)?.computeType(expression)
  }
}