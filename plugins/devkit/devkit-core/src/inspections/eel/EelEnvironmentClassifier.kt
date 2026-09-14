// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections.eel

import com.intellij.codeInspection.isInheritorOf
import com.intellij.psi.PsiType
import org.jetbrains.idea.devkit.inspections.path.PathAnnotationInfo
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UQualifiedReferenceExpression
import org.jetbrains.uast.UReferenceExpression
import org.jetbrains.uast.UVariable
import org.jetbrains.uast.resolveToUElement

internal enum class EelEnvironment {
  HOST,
  TARGET,
  MIXED,
  UNKNOWN,
}

internal object EelEnvironmentClassifier {
  fun classify(expression: UExpression): EelEnvironment = classify(expression, hashSetOf())

  fun classify(type: PsiType): EelEnvironment = when {
    type.isLocalEelType() -> EelEnvironment.HOST
    type.isEelType() -> EelEnvironment.TARGET
    else -> EelEnvironment.UNKNOWN
  }

  private fun classify(expression: UExpression, visited: MutableSet<UExpression>): EelEnvironment {
    if (!visited.add(expression)) return EelEnvironment.UNKNOWN
    if (expression is ULambdaExpression) return EelEnvironment.UNKNOWN

    if (expression is UQualifiedReferenceExpression) {
      val receiver = classify(expression.receiver, visited)
      if (receiver != EelEnvironment.UNKNOWN) return receiver
    }
    if (expression is UCallExpression) {
      val receiver = expression.receiver?.let { classify(it, visited) }
      if (receiver != null && receiver != EelEnvironment.UNKNOWN) return receiver
    }

    when (PathAnnotationInfo.forExpression(expression)) {
      PathAnnotationInfo.LocalPathInfo -> return EelEnvironment.HOST
      else -> Unit
    }

    if (expression is UReferenceExpression) {
      val variable = expression.resolveToUElement() as? UVariable
      variable?.uastInitializer?.let { return classify(it, visited) }
    }

    if (expression is UQualifiedReferenceExpression) {
      val receiver = classify(expression.receiver, visited)
      val selectorArguments = (expression.selector as? UCallExpression)?.valueArguments.orEmpty()
      return selectorArguments.fold(receiver) { result, argument -> merge(result, classify(argument, visited)) }
    }

    if (expression is UCallExpression) {
      val method = expression.resolve()
      val containingClass = method?.containingClass?.qualifiedName
      if (containingClass == "com.intellij.openapi.application.PathManager") return EelEnvironment.HOST

      val receiver = expression.receiver?.let { classify(it, visited) } ?: EelEnvironment.UNKNOWN
      if (receiver != EelEnvironment.UNKNOWN) {
        return expression.valueArguments.fold(receiver) { result, argument -> merge(result, classify(argument, visited)) }
      }

      if (expression.returnType?.isLocalEelType() == true) return EelEnvironment.HOST
      if (expression.returnType?.isEelType() == true) {
        return EelEnvironment.TARGET
      }

      return expression.valueArguments.fold(receiver) { result, argument -> merge(result, classify(argument, visited)) }
    }

    if (expression.getExpressionType()?.isLocalEelType() == true) return EelEnvironment.HOST
    if (expression.getExpressionType()?.isEelType() == true) return EelEnvironment.TARGET

    return EelEnvironment.UNKNOWN
  }

  private fun merge(first: EelEnvironment, second: EelEnvironment): EelEnvironment {
    if (first == EelEnvironment.UNKNOWN) return second
    if (second == EelEnvironment.UNKNOWN) return first
    if (first == second) return first
    return EelEnvironment.MIXED
  }

  private fun PsiType.isEelType(): Boolean =
    isInheritorOf("com.intellij.platform.eel.EelDescriptor") ||
    isInheritorOf("com.intellij.platform.eel.EelApi") ||
    isInheritorOf("com.intellij.platform.eel.path.EelPath")

  private fun PsiType.isLocalEelType(): Boolean =
    isInheritorOf("com.intellij.platform.eel.provider.LocalEelDescriptor") ||
    isInheritorOf("com.intellij.platform.eel.LocalEelApi")
}
