// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyze
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.kotlin.resolve.bindingContextUtil.getTargetFunctionDescriptor
import org.jetbrains.kotlin.resolve.calls.util.getResolvedCall
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class RedundantElvisReturnNullInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return returnExpressionVisitor(fun(innerReturnExpression: KtReturnExpression) {
            if ((innerReturnExpression.returnedExpression?.deparenthesize() as? KtConstantExpression)?.text != KtTokens.NULL_KEYWORD.value) return

            val elvisExpression = innerReturnExpression.getStrictParentOfType<KtBinaryExpression>() ?: return
            if (elvisExpression.operationToken != KtTokens.ELVIS) return

            val outerReturnExpression = elvisExpression.getStrictParentOfType<KtReturnExpression>() ?: return
            if (elvisExpression != outerReturnExpression.returnedExpression?.deparenthesize()) return

            val bindingContext = outerReturnExpression.safeAnalyze(BodyResolveMode.PARTIAL)

            val outerReturnTarget = outerReturnExpression.getTargetFunctionDescriptor(bindingContext) ?: return
            val innerReturnTarget = innerReturnExpression.getTargetFunctionDescriptor(bindingContext) ?: return

            // Returns point to different targets, we cannot remove the inner return
            if (outerReturnTarget != innerReturnTarget) return

            val right = elvisExpression.right?.deparenthesize()?.takeIf { it == innerReturnExpression } ?: return
            if (elvisExpression.left?.getResolvedCall(bindingContext)?.resultingDescriptor?.returnType?.isMarkedNullable != true) return

            holder.registerProblem(
                elvisExpression,
                KotlinBundle.message("inspection.redundant.elvis.return.null.descriptor"),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                TextRange(elvisExpression.operationReference.startOffset, right.endOffset).shiftLeft(elvisExpression.startOffset),
                RemoveRedundantElvisReturnNull()
            )
        })
    }

    private fun KtExpression.deparenthesize() = KtPsiUtil.deparenthesize(this)

    private class RemoveRedundantElvisReturnNull : LocalQuickFix {
        override fun getName() = KotlinBundle.message("remove.redundant.elvis.return.null.text")

        override fun getFamilyName() = name

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val binaryExpression = descriptor.psiElement as? KtBinaryExpression ?: return
            val left = binaryExpression.left ?: return
            binaryExpression.replace(left)
        }
    }
}
