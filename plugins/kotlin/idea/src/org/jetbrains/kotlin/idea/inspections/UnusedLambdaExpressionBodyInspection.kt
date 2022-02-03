// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.inspections

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.builtins.isFunctionType
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.util.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.allChildren
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.tower.NewResolvedCallImpl
import org.jetbrains.kotlin.resolve.calls.tower.NewVariableAsFunctionResolvedCallImpl
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.utils.addToStdlib.safeAs


class UnusedLambdaExpressionBodyInspection : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return callExpressionVisitor(fun(expression) {
            val context = expression.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_WITH_CFA)
            if (context == BindingContext.EMPTY || expression.isUsedAsExpression(context)) {
                return
            }

            val resolvedCall = expression.getResolvedCall(context) ?: return
            val descriptor = resolvedCall.resultingDescriptor.let {
                if (resolvedCall is NewResolvedCallImpl || resolvedCall is NewVariableAsFunctionResolvedCallImpl) it.original else it
            }
            if (!descriptor.returnsFunction()) {
                return
            }

            val function = descriptor.source.getPsi() as? KtFunction ?: return
            if (function.hasBlockBody() || function.bodyExpression !is KtLambdaExpression) {
                return
            }

            holder.registerProblem(
                expression,
                KotlinBundle.message("unused.return.value.of.a.function.with.lambda.expression.body"),
                RemoveEqTokenFromFunctionDeclarationFix(function)
            )
        })
    }

    private fun CallableDescriptor.returnsFunction() = returnType?.isFunctionType ?: false

    class RemoveEqTokenFromFunctionDeclarationFix(function: KtFunction) : LocalQuickFixOnPsiElement(function) {
        override fun getText(): String = KotlinBundle.message("remove.token.from.function.declaration")

        override fun getFamilyName(): String = name

        override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
            val function = startElement as? KtFunction ?: return
            if (!FileModificationService.getInstance().preparePsiElementForWrite(function)) {
                return
            }

            function.replaceBlockExpressionWithLambdaBody(function.bodyExpression?.safeAs<KtLambdaExpression>()?.bodyExpression)
        }
    }

    companion object {
        fun KtDeclarationWithBody.replaceBlockExpressionWithLambdaBody(lambdaBody: KtBlockExpression?) {
            equalsToken?.let { token ->
                val ktPsiFactory = KtPsiFactory(project)
                val lambdaBodyRange = lambdaBody?.allChildren
                val newBlockBody: KtBlockExpression = if (lambdaBodyRange?.isEmpty == false) {
                    ktPsiFactory.createDeclarationByPattern<KtNamedFunction>("fun foo() {$0}", lambdaBodyRange).bodyBlockExpression!!
                } else {
                    ktPsiFactory.createBlock("")
                }

                bodyExpression?.delete()
                token.replace(newBlockBody)
            }
        }
    }
}