// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.util.REQUIRES_BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.kotlin.analysis.api.calls.KtFunctionCall
import org.jetbrains.kotlin.analysis.api.types.KtFunctionalType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

internal val requiresBlockingContextAnnotation = FqName(REQUIRES_BLOCKING_CONTEXT_ANNOTATION)
internal val requiresBlockingContextAnnotationId = ClassId.topLevel(requiresBlockingContextAnnotation)

internal abstract class BlockingContextFunctionBodyVisitor : KtTreeVisitorVoid() {
  override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression): Unit = Unit

  override fun visitDeclaration(dcl: KtDeclaration) {
    if (dcl is KtVariableDeclaration) {
      dcl.initializer?.accept(this)
    }
  }

  protected fun checkInlineLambdaArguments(call: KtFunctionCall<*>) {
    for ((psi, descriptor) in call.argumentMapping) {
      if (
        descriptor.returnType is KtFunctionalType &&
        !descriptor.symbol.isCrossinline &&
        !descriptor.symbol.isNoinline &&
        psi is KtLambdaExpression
      ) {
        psi.bodyExpression?.accept(this)
      }
    }
  }
}

internal fun extractElementToHighlight(expression: KtCallExpression): KtElement = expression.getCallNameExpression() ?: expression