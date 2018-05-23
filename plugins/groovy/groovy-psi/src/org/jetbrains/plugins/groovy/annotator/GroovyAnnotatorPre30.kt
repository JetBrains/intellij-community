// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression

internal class GroovyAnnotatorPre30(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitBinaryExpression(expression: GrBinaryExpression) {
    super.visitBinaryExpression(expression)
    val operator = expression.operationToken
    val tokenType = operator.node.elementType
    if (tokenType === GroovyElementTypes.T_ID || tokenType === GroovyElementTypes.T_NID) {
      holder.createErrorAnnotation(operator, GroovyBundle.message("operator.is.not.supported.in", tokenType))
    }
  }
}
