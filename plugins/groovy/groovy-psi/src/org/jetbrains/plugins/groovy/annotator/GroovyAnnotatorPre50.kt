// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.PropertyKey
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes.mIMPL
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.api.GrArrayInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrPatternVariable
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression

class GroovyAnnotatorPre50(val holder : AnnotationHolder) : GroovyElementVisitor() {
  override fun visitPatternVariable(variable: GrPatternVariable) {
    error(variable, Groovy5Features.PATTERN_MATCHING)
  }

  override fun visitBinaryExpression(expression: GrBinaryExpression) {
    if (expression.operator == mIMPL) {
      error(expression.operationToken, Groovy5Features.LOGICAL_IMPLICATION)
    }
  }

  override fun visitNewExpression(newExpression: GrNewExpression) {
    val arrayDeclaration = newExpression.arrayDeclaration
    val arrayInitializer = newExpression.arrayInitializer
    if (arrayDeclaration == null || arrayInitializer == null) return

    if(arrayInitializer.expressions.any { it is GrArrayInitializer }) {
      error(newExpression, Groovy5Features.MULTI_DIMENSIONAL_ARRAY_INITIALIZER)
    }
  }

  private fun error(element: PsiElement, feature: Groovy5Features) {
    val message = GroovyBundle.message(feature.messageKey)
    holder.newAnnotation(
      HighlightSeverity.ERROR,
      GroovyBundle.message("inspection.available.with.groovy.5.or.later", message)
    )
      .range(element)
      .create()
  }

  private enum class Groovy5Features(val messageKey: @PropertyKey(resourceBundle = GroovyBundle.BUNDLE) String) {
    PATTERN_MATCHING("instanceof.pattern.variable.feature"),
    LOGICAL_IMPLICATION("logical.implication.feature"),
    MULTI_DIMENSIONAL_ARRAY_INITIALIZER("multi.dimensional.array.initializer.feature")
  }
}