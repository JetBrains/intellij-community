// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaFunctionType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createDeclarationByPattern
import org.jetbrains.kotlin.psi.psiUtil.allChildren

internal class UnusedLambdaExpressionBodyInspection : KotlinApplicableInspectionBase.Simple<KtCallExpression, UnusedLambdaExpressionBodyInspection.Context>() {
    internal data class Context(
        val function: KtFunction
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = true

    override fun KaSession.prepareContext(element: KtCallExpression): Context? {
        if (element.isUsedAsExpression || (element.parent as? KtCallExpression)?.calleeExpression == element) {
            return null
        }

        val resolvedCall = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val symbol = resolvedCall.partiallyAppliedSymbol.symbol
        if (symbol.returnType !is KaFunctionType) {
            return null
        }

        val function = symbol.psi as? KtFunction ?: return null
        if (function.hasBlockBody() || function.bodyExpression !is KtLambdaExpression) {
            return null
        }

        return Context(function)
    }

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun getProblemDescription(element: KtCallExpression, context: Context): @InspectionMessage String =
        KotlinBundle.message("unused.return.value.of.a.function.with.lambda.expression.body")

    override fun createQuickFix(
        element: KtCallExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtCallExpression> = RemoveEqTokenFromFunctionDeclarationFix(context)

    private class RemoveEqTokenFromFunctionDeclarationFix(
        private val context: Context
    ) : KotlinModCommandQuickFix<KtCallExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("remove.token.from.function.declaration")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            val function = updater.getWritable(context.function) ?: return
            val lambdaBody = (function.bodyExpression as? KtLambdaExpression)?.bodyExpression
            val equalsToken = function.equalsToken
            if (equalsToken == null) return

            val ktPsiFactory = KtPsiFactory(project)
            val lambdaBodyRange = lambdaBody?.allChildren
            val newBlockBody: KtBlockExpression = if (lambdaBodyRange?.isEmpty == false) {
                ktPsiFactory.createDeclarationByPattern<KtNamedFunction>("fun foo() {$0}", lambdaBodyRange).bodyBlockExpression!!
            } else {
                ktPsiFactory.createBlock("")
            }

            function.bodyExpression?.delete()
            equalsToken.replace(newBlockBody)
        }
    }
}
