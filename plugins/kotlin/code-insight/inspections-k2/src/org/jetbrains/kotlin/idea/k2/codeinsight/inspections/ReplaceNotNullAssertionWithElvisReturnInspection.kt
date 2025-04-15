// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.symbols.KaFunctionSymbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.psi.getParentLambdaLabelName
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getParentOfTypes
import org.jetbrains.kotlin.psi.psiUtil.getTopmostParentOfType

internal class ReplaceNotNullAssertionWithElvisReturnInspection :
    KotlinApplicableInspectionBase.Simple<KtPostfixExpression, ReplaceNotNullAssertionWithElvisReturnInspection.Context>() {

    data class Context(val returnNull: Boolean, val returnLabelName: String?)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = postfixExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtPostfixExpression): Boolean {
        if (element.baseExpression == null) return false
        val operationReference = element.operationReference
        if (operationReference.getReferencedNameElementType() != KtTokens.EXCLEXCL) return false

        if ((element.getTopmostParentOfType<KtParenthesizedExpression>() ?: element).parent is KtReturnExpression) return false

        return element.getParentOfTypes(
            strict = true,
            KtLambdaExpression::class.java,
            KtNamedFunction::class.java
        ) != null
    }

    override fun getApplicableRanges(element: KtPostfixExpression): List<TextRange> =
        ApplicabilityRange.single(element) { it.operationReference }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtPostfixExpression): Context? {
        val parent = element.getParentOfTypes(
            strict = true,
            KtLambdaExpression::class.java,
            KtNamedFunction::class.java
        ) ?: return null

        if (element.diagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS).any {
                it is KaFirDiagnostic.UnnecessaryNotNullAssertion
            }
        ) return null

        val (isNullable, returnLabelName) = when (parent) {
            is KtNamedFunction -> {
                val returnType = parent.getReturnType(analysisSession = this) ?: return null
                val isNullable = returnType.canBeNull
                if (!returnType.isUnitType && !isNullable) return null
                isNullable to null
            }

            is KtLambdaExpression -> {
                val functionLiteral = parent.functionLiteral
                val returnType = functionLiteral.getReturnType(analysisSession = this) ?: return null
                if (!returnType.isUnitType) return null
                val lambdaLabelName = functionLiteral.bodyBlockExpression?.getParentLambdaLabelName() ?: return null
                false to lambdaLabelName
            }

            else -> return null
        }

        return Context(isNullable, returnLabelName)
    }

    override fun getProblemDescription(
        element: KtPostfixExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replace.with.return")

    override fun getProblemHighlightType(
        element: KtPostfixExpression,
        context: Context,
    ): ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING

    override fun createQuickFix(
        element: KtPostfixExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtPostfixExpression> = object : KotlinModCommandQuickFix<KtPostfixExpression>(), LowPriorityAction {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.elvis.return.fix.text", returnValueText(context.returnNull))

        override fun applyFix(
            project: Project,
            element: KtPostfixExpression,
            updater: ModPsiUpdater
        ) {
            val base = element.baseExpression ?: return
            val psiFactory = KtPsiFactory(project)
            element.replaced(
                psiFactory.createExpressionByPattern(
                    "$0 ?: return$1$2",
                    base,
                    context.returnLabelName?.let { "@$it" } ?: "",
                    returnValueText(context.returnNull)
                )
            )
        }
    }
}

private fun KtFunction.getReturnType(
    analysisSession: KaSession,
): KaType? = with(analysisSession) {
    (symbol as? KaFunctionSymbol)?.returnType
}

private fun returnValueText(returnNull: Boolean): String =
    if (returnNull) " null" else ""
