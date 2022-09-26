// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.util.asSafely
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression

class GroovyMacroManipulator : AbstractElementManipulator<GrReferenceExpression>() {
  override fun handleContentChange(element: GrReferenceExpression, range: TextRange, newContent: String): GrReferenceExpression? {
    val refExpr = GroovyPsiElementFactory.getInstance(element.project).createReferenceExpressionFromText(newContent)
    return element.replace(refExpr)?.asSafely<GrReferenceExpression>()
  }
}