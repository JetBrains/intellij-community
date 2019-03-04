// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.getTopLevelTypeCached

data class ExpressionArgument(val expression: GrExpression) : Argument {

  override val type: PsiType? get() = expression.type

  override val topLevelType: PsiType? get() = getTopLevelTypeCached(expression)
}
