// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.transformations.inline

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.typing.GrTypeCalculator

class GroovyInlineTransformationTypeCalculator: GrTypeCalculator<GrExpression> {

  override fun getType(expression: GrExpression): PsiType? = getTypeFromInlineTransformation(expression)
}