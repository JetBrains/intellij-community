// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.idea.devkit.inspections.CancellationExceptionHandlingChecker
import org.jetbrains.idea.devkit.kotlin.util.getContext
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.annotations.KaAnnotationValue
import org.jetbrains.kotlin.analysis.api.symbols.KaCallableSymbol
import org.jetbrains.kotlin.analysis.api.symbols.name
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtTryExpression

private val cancellationExceptionClassId = ClassId.fromString("kotlinx/coroutines/CancellationException")
private val throwsClassId = ClassId.fromString("kotlin/jvm/Throws")

internal class KtCancellationExceptionHandlingChecker : CancellationExceptionHandlingChecker {

  override fun isSuspicious(catchParameter: PsiElement): Boolean {
    val parameter = catchParameter as? KtParameter ?: return false
    if (getContext(parameter).isSuspending()) {
      analyze(parameter) {
        val catchParameterType = parameter.typeReference?.type ?: return false
        val cancellationException = buildClassType(cancellationExceptionClassId)
        return catchParameterType.semanticallyEquals(cancellationException)
      }
    }
    return false
  }

  override fun getCeName(): String {
    return "kotlinx.coroutines.CancellationException"
  }

  override fun containsSuspiciousCeCatchClause(tryExpression: PsiElement): Boolean {
    val parameter = tryExpression as? KtTryExpression ?: return false
    if (getContext(parameter).isSuspending()) {
      return analyze(parameter) {
        parameter.catchClauses
          .any { it.catchParameter?.expressionType?.semanticallyEquals(buildCancellationExceptionType()) == true }
      }
    }
    return false
  }

  override fun findCeThrowingExpressionName(tryExpression: PsiElement): String? {
    val ktTryExpression = tryExpression as? KtTryExpression ?: return null
    for (expression in ktTryExpression.tryBlock.statements.filterIsInstance<KtCallExpression>()) {
      analyze(expression) {
        // workaround with calleeExpression as expression.mainReference?.resolveToSymbol() doesn't work
        val symbol = expression.calleeExpression?.mainReference?.resolveToSymbol() as? KaCallableSymbol ?: return@analyze
        if (throwsCe(symbol)) {
          return symbol.name?.asString()
        }
      }
    }
    return null
  }

  private fun KaSession.throwsCe(symbol: KaCallableSymbol): Boolean {
    val exceptionClasses = symbol.annotations.firstOrNull { it.classId == throwsClassId }
                             ?.arguments?.firstOrNull { it.name.asString() == "exceptionClasses" }
                             ?.expression as? KaAnnotationValue.ArrayValue ?: return false
    return exceptionClasses.values
      .filterIsInstance<KaAnnotationValue.ClassLiteralValue>()
      .any { it.type.semanticallyEquals(buildCancellationExceptionType()) }
  }

  private fun KaSession.buildCancellationExceptionType(): KaType =
    buildClassType(cancellationExceptionClassId)

}
