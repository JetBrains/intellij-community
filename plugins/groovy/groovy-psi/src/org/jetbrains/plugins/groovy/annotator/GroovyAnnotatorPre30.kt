// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty

internal class GroovyAnnotatorPre30(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitBinaryExpression(expression: GrBinaryExpression) {
    super.visitBinaryExpression(expression)
    val operator = expression.operationToken
    val tokenType = operator.node.elementType
    if (tokenType === GroovyElementTypes.T_ID || tokenType === GroovyElementTypes.T_NID) {
      holder.createErrorAnnotation(operator, message("operator.is.not.supported.in", tokenType))
    }
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    super.visitAssignmentExpression(expression)
    val operator = expression.operationToken
    if (operator.node.elementType === GroovyElementTypes.T_ELVIS_ASSIGN) {
      holder.createErrorAnnotation(operator, message("unsupported.elvis.assignment"))
    }
  }

  override fun visitIndexProperty(expression: GrIndexProperty) {
    super.visitIndexProperty(expression)
    val safeAccessToken = expression.safeAccessToken
    if (safeAccessToken != null) {
      holder.createErrorAnnotation(safeAccessToken, message("unsupported.safe.index.access"))
    }
  }
}
