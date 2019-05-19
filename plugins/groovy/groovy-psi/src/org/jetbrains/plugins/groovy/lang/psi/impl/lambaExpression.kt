// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl

import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.resolve.MethodTypeInferencer

internal fun calculateReturnType(expression: GrLambdaExpressionImpl) : PsiType? {
  val body = expression.body
  if (body is GrCodeBlock) {
    return GroovyPsiManager.inferType(expression, MethodTypeInferencer(body))
  }
  if (body is GrExpression) {
    return body.type
  }
  return null
}