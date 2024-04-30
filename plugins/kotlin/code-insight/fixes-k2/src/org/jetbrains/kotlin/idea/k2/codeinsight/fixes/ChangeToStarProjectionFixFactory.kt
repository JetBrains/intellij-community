// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.fixes

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.KtStarTypeProjection
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KtFirDiagnostic
import org.jetbrains.kotlin.analysis.api.types.KtNonErrorClassType
import org.jetbrains.kotlin.analysis.api.types.KtTypeParameterType
import org.jetbrains.kotlin.idea.base.psi.typeArguments
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.fixes.KotlinQuickFixFactory
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.isAsKeyword
import org.jetbrains.kotlin.idea.quickfix.StarProjectionUtils.isOnJvm
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes3
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

internal object ChangeToStarProjectionFixFactory {
    val uncheckedCastFactory = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.UncheckedCast ->
        val quickFix = getQuickFix(diagnostic.psi) ?: return@ModCommandBased emptyList()
        listOf(quickFix)
    }

    val cannotCheckForErased = KotlinQuickFixFactory.ModCommandBased { diagnostic: KtFirDiagnostic.CannotCheckForErased ->
        val element = diagnostic.psi

        // We don't suggest this quick-fix for array instance checks because there is ConvertToIsArrayOfCallFix
        if (element.parent is KtIsExpression
            && diagnostic.type.isArrayOrPrimitiveArray()
            && element.isOnJvm()
        ) return@ModCommandBased emptyList()

        val quickFix = getQuickFix(element) ?: return@ModCommandBased emptyList()
        listOf(quickFix)
    }

    context(KtAnalysisSession)
    private fun getQuickFix(element: PsiElement): ChangeToStarProjectionFix? {
        val (binaryExpr, typeReference, typeElement) = StarProjectionUtils.getChangeToStarProjectionFixInfo(element) ?: return null

        if (binaryExpr?.operationReference?.isAsKeyword() == true) {
            val parent = binaryExpr.getParentOfTypes3<KtValueArgument, KtQualifiedExpression, KtCallableDeclaration>()
            if (parent is KtCallableDeclaration
                && parent.typeReference.typeArguments().any { it.projectionKind != KtProjectionKind.STAR }
                && typeReference.typeArguments().isNotEmpty()
                && binaryExpr.isUsedAsExpression()
            ) return null

            val type = when (parent) {
                is KtValueArgument -> {
                    val callExpr = parent.getStrictParentOfType<KtCallExpression>()
                    val functionCall = callExpr?.resolveCall()?.successfulFunctionCallOrNull()
                    functionCall?.argumentMapping?.get(parent.getArgumentExpression())?.symbol?.returnType
                }

                is KtQualifiedExpression ->
                    if (KtPsiUtil.safeDeparenthesize(parent.receiverExpression) == binaryExpr)
                        parent.resolveCall()?.successfulFunctionCallOrNull()?.partiallyAppliedSymbol?.symbol?.receiverParameter?.type
                    else
                        null

                else ->
                    null
            }
            val typeArguments = (type as? KtNonErrorClassType)?.ownTypeArguments
            if (typeArguments?.any { it !is KtStarTypeProjection && it.type !is KtTypeParameterType } == true) return null
        }

        return if (typeElement.typeArgumentsAsTypes.isEmpty()) null
        else ChangeToStarProjectionFix(typeElement)
    }

    private class ChangeToStarProjectionFix(
        element: KtTypeElement,
    ) : KotlinPsiUpdateModCommandAction.ElementBased<KtTypeElement, Unit>(element, Unit) {

        override fun invoke(
            actionContext: ActionContext,
            element: KtTypeElement,
            elementContext: Unit,
            updater: ModPsiUpdater,
        ) = StarProjectionUtils.changeToStarProjection(actionContext.project, element)

        override fun getActionName(
            actionContext: ActionContext,
            element: KtTypeElement,
            elementContext: Unit,
        ): String = StarProjectionUtils.changeToStarProjectionActionName(element)

        override fun getFamilyName(): String = KotlinBundle.message("fix.change.to.star.projection.family")
    }
}
