// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.groovy.GroovyBundle.message
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrRegex
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList

class GroovyAnnotatorPre18(private val holder: AnnotationHolder, private val version: String) : GroovyElementVisitor() {

  override fun visitTypeArgumentList(typeArgumentList: GrTypeArgumentList) {
    if (typeArgumentList.isDiamond) {
      holder.createErrorAnnotation(typeArgumentList, message("unsupported.diamonds.0", version))
    }
  }

  override fun visitMethodCall(call: GrMethodCall) {
    if (call.isCommandExpression) {
      holder.createErrorAnnotation(call, message("unsupported.command.syntax.0", version))
    }
  }

  override fun visitRegexExpression(expression: GrRegex) {
    val tokenType = expression.node.firstChildNode.elementType
    if (tokenType == DOLLAR_SLASHY_BEGIN) {
      holder.createErrorAnnotation(expression, message("unsupported.dollar.slashy.string.0", version))
    }
    else if (tokenType == SLASHY_BEGIN) {
      highlightMultilineSlashyString(expression)
    }
  }

  override fun visitLiteralExpression(literal: GrLiteral) {
    val tokenType = literal.node.firstChildNode.elementType
    if (tokenType == DOLLAR_SLASHY_LITERAL) {
      holder.createErrorAnnotation(literal, message("unsupported.dollar.slashy.string.0", version))
    }
    else if (tokenType == SLASHY_LITERAL) {
      highlightMultilineSlashyString(literal)
    }
  }

  private fun highlightMultilineSlashyString(string: GroovyPsiElement) {
    if (string.text.let { "\n" in it || "\r" in it }) {
      holder.createErrorAnnotation(string, message("unsupported.multiline.slashy.string.0", version))
    }
  }
}
