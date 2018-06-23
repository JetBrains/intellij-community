// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.annotator.intentions.ReplaceDotFix
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrDoWhileStatement
import org.jetbrains.plugins.groovy.lang.psi.api.GrInExpression
import org.jetbrains.plugins.groovy.lang.psi.api.GrTryResourceList
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifierList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrInstanceOfExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrIndexProperty

internal class GroovyAnnotatorPre30(private val holder: AnnotationHolder) : GroovyElementVisitor() {

  override fun visitModifierList(modifierList: GrModifierList) {
    val modifier = modifierList.getModifier(PsiModifier.DEFAULT) ?: return
    holder.createErrorAnnotation(modifier, GroovyBundle.message("default.modifier.in.old.versions"))
  }

  override fun visitDoWhileStatement(statement: GrDoWhileStatement) {
    super.visitDoWhileStatement(statement)
    holder.createErrorAnnotation(statement.doKeyword, message("unsupported.do.while.statement"))
  }

  override fun visitTryResourceList(resourceList: GrTryResourceList) {
    super.visitTryResourceList(resourceList)
    holder.createErrorAnnotation(resourceList.firstChild, message("unsupported.resource.list"))
  }

  override fun visitBinaryExpression(expression: GrBinaryExpression) {
    super.visitBinaryExpression(expression)
    val operator = expression.operationToken
    val tokenType = operator.node.elementType
    if (tokenType === T_ID || tokenType === T_NID) {
      holder.createErrorAnnotation(operator, message("operator.is.not.supported.in", tokenType))
    }
  }

  override fun visitInExpression(expression: GrInExpression) {
    super.visitInExpression(expression)
    val negation = expression.negationToken
    if (negation != null) {
      holder.createErrorAnnotation(negation, message("unsupported.negated.in"))
    }
  }

  override fun visitInstanceofExpression(expression: GrInstanceOfExpression) {
    super.visitInstanceofExpression(expression)
    val negation = expression.negationToken
    if (negation != null) {
      holder.createErrorAnnotation(negation, message("unsupported.negated.instanceof"))
    }
  }

  override fun visitAssignmentExpression(expression: GrAssignmentExpression) {
    super.visitAssignmentExpression(expression)
    val operator = expression.operationToken
    if (operator.node.elementType === T_ELVIS_ASSIGN) {
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

  override fun visitReferenceExpression(expression: GrReferenceExpression) {
    super.visitReferenceExpression(expression)
    val dot = expression.dotToken ?: return
    val tokenType = dot.node.elementType
    if (tokenType === T_METHOD_REFERENCE) {
      holder.createErrorAnnotation(dot, message("operator.is.not.supported.in", tokenType)).apply {
        val descriptor = createDescriptor(dot)
        val fix = ReplaceDotFix(tokenType, T_METHOD_CLOSURE)
        registerFix(fix, descriptor)
      }
    }
  }
}
