// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.codeInspection.type.highlighting

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import org.jetbrains.plugins.groovy.GroovyBundle
import org.jetbrains.plugins.groovy.codeInspection.assignment.GrCastFix
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitorHelper.hasErrorElements
import org.jetbrains.plugins.groovy.codeInspection.type.GroovyTypeCheckVisitorHelper.isOperatorWithSimpleTypes
import org.jetbrains.plugins.groovy.highlighting.HighlightSink
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.util.isFake
import org.jetbrains.plugins.groovy.lang.resolve.api.*

class BinaryExpressionHighlighter(val expression: GrBinaryExpression,
                                  reference: GroovyCallReference,
                                  sink: HighlightSink) : CallReferenceHighlighter(reference, sink) {

  override val unknownArgsMessage: String
    get() = GroovyBundle.message("cannot.infer.argument.types")
  override val ambiguousMethodMessage: String
    get() = GroovyBundle.message("operator.call.is.ambiguous")

  override fun getInapplicableMethodMessage(result: GroovyMethodResult, containingType: PsiType, arguments: Arguments): String {
    val typeText = containingType.internalCanonicalText
    val argumentsString = argumentsString(arguments)
    return GroovyBundle.message("cannot.apply.method1", result.element.name, typeText, argumentsString)
  }

  override fun getHighlightElement(): PsiElement = expression.operationToken

  override fun buildFix(argument: Argument, expectedType: PsiType): GrCastFix? {
    if (argument !is ExpressionArgument) return null
    val arguments = reference.arguments ?: return null
    if (argument !in arguments) return null

    val name = "Cast operand to " + expectedType.presentableText
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
