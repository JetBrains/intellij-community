// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.assignment.GrCastFix
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitorHelper.hasErrorElements
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitorHelper.isOperatorWithSimpleTypes
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.util.isFake
import org.jetbrains.plugins.groovy.lang.resolve.api.ExpressionArgument
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyCallReference

class BinaryExpressionHighlighter(
  private val expression: GrBinaryExpression,
  reference: GroovyCallReference,
  sink: HighlightSink
) : CallReferenceHighlighter(reference, sink) {

  override val ambiguousMethodMessage: String get() = GroovyBundle.message("operator.call.is.ambiguous")

  override val highlightElement: PsiElement get() = expression.operationToken

  override fun buildCastFix(argument: ExpressionArgument, expectedType: PsiType): GrCastFix? {
    val arguments = reference.arguments ?: return null
    if (argument !in arguments) return null

    val name = GroovyBundle.message("intention.name.cast.operand.to.0", expectedType.presentableText)
    return GrCastFix(expectedType, argument.expression, true, name)
  }

  fun highlight() {
    if (expression.isFake()) return
    if (hasErrorElements(expression)) return

    val reference = expression.reference ?: return
    val resolveResult = reference.advancedResolve()

    if (isOperatorWithSimpleTypes(expression, resolveResult)) return

    highlightMethodApplicability()
  }
}
