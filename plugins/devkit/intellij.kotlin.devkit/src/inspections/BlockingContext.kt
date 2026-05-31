// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.kotlin.inspections

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.idea.devkit.kotlin.DevKitKotlinBundle
import org.jetbrains.idea.devkit.util.REQUIRES_BLOCKING_CONTEXT_ANNOTATION
import org.jetbrains.kotlin.analysis.api.resolution.KaFunctionCall
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.CleanupFix
import org.jetbrains.kotlin.idea.core.moveCaret
import org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFixBase
import org.jetbrains.kotlin.idea.quickfix.replaceWith.ReplaceWithData
import org.jetbrains.kotlin.idea.refactoring.inline.codeInliner.UsageReplacementStrategy
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.KtVariableDeclaration
import org.jetbrains.kotlin.psi.psiUtil.getCallNameExpression
import org.jetbrains.kotlin.resolve.calls.util.getCalleeExpressionIfAny

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

internal class ReplaceWithSuspendAlternativeFix(
  element: KtSimpleNameExpression,
  replaceWith: ReplaceWithData,
) : DeprecatedSymbolUsageFixBase(element, replaceWith), HighPriorityAction, CleanupFix, LocalQuickFix {
  override fun getFamilyName(): String = DevKitKotlinBundle.message(
    "inspections.forbidden.method.in.suspend.context.replace.with.suspend.alternative.fix.text")

  override fun getText(): String = familyName

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    super.invoke(project, null, descriptor.psiElement.containingFile)
  }

  override fun invoke(replacementStrategy: UsageReplacementStrategy, project: Project, editor: Editor?) {
    val element = element ?: return
    val replacer = replacementStrategy.createReplacer(element) ?: return
    val result = replacer() ?: return

    if (editor != null) {
      val offset = (result.getCalleeExpressionIfAny() ?: result).textOffset
      editor.moveCaret(offset)
    }
  }
}