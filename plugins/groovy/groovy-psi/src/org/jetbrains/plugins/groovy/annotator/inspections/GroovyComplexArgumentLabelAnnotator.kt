// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.inspections

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*
import org.jetbrains.plugins.groovy.lang.psi.api.GrLambdaExpression
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrParenthesizedExpression
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

class GroovyComplexArgumentLabelAnnotator : Annotator {
  override fun annotate(argument: PsiElement, holder: AnnotationHolder) {
    if (argument !is GrArgumentLabel) return
    val element = argument.nameElement
    if (element !is GrExpression) return
    val elementType = element.elementType ?: return

    if (elementType == STRING_SQ || elementType == STRING_DQ || elementType == STRING_TSQ || elementType == STRING_TDQ) return

    if (element is GrLiteral || element is GrListOrMap || element is GrLambdaExpression || element is GrClosableBlock) return

    if (element is GrParenthesizedExpression) return


    holder.newAnnotation(HighlightSeverity.ERROR, GroovyBundle.message("groovy.complex.argument.label.annotator.message")).range(argument)
      .withFix(GroovyComplexArgumentLabelQuickFix(element)).create()
  }
}