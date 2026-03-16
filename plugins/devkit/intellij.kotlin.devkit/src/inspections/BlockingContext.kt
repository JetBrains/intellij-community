// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import org.jetbrains.idea.devkit.util.REQUIRES_BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression

internal val RequiresBlockingContextAnnotation: FqName = FqName(REQUIRES_BLOCKING_CONTEXT_ANNOTATION)
internal val RequiresBlockingContextAnnotationId: ClassId = ClassId.topLevel(RequiresBlockingContextAnnotation)

internal abstract class BlockingContextFunctionBodyVisitor : KtTreeVisitorVoid() {
  override fun visitLambdaExpression(lambdaExpression: KtLambdaExpression): Unit = Unit

  override fun visitDeclaration(dcl: KtDeclaration) {
    if (dcl is KtVariableDeclaration) {
      dcl.initializer?.accept(this)
    }
  }

  protected fun checkInlineLambdaArguments(call: KaFunctionCall<*>) {
    for ((psi, descriptor) in call.argumentMapping) {
      if (
        descriptor.returnType is KaFunctionType &&
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