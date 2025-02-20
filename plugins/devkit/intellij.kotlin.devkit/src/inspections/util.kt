// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid

internal class TopLevelFunctionVisitor(val blockBodyVisitor: Lazy<KtTreeVisitorVoid>) : KtTreeVisitorVoid() {
  override fun visitElement(element: PsiElement): Unit = Unit

  override fun visitNamedFunction(function: KtNamedFunction) {
    if (function.hasModifier(KtTokens.SUSPEND_KEYWORD) && !isSuspensionRestricted(function)) {
      function.bodyExpression?.accept(blockBodyVisitor.value)
      return
    }
    super.visitNamedFunction(function)
  }

  override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression) {
    analyze(lambdaExpression) {
      val type = lambdaExpression.expressionType
      if (type?.isSuspendFunctionType == true && !isSuspensionRestricted(type)) {
        lambdaExpression.bodyExpression?.accept(blockBodyVisitor.value)
        return
      }
    }

    super.visitLambdaExpression(lambdaExpression)
  }
}