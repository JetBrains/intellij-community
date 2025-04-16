// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.FoldInitializerAndIfExpressionData
import org.jetbrains.kotlin.idea.codeInsight.joinLines
import org.jetbrains.kotlin.idea.codeInsight.prepareData
import org.jetbrains.kotlin.idea.codeInsight.replaceVarWithVal
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

internal class FoldInitializerAndIfToElvisInspection :
    KotlinApplicableInspectionBase.Simple<KtIfExpression, FoldInitializerAndIfExpressionData>() {

    override fun getProblemHighlightType(
        element: KtIfExpression,
        context: FoldInitializerAndIfExpressionData,
    ): ProblemHighlightType = when (element.condition) {
        is KtBinaryExpression -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        else -> ProblemHighlightType.INFORMATION
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtIfExpression): FoldInitializerAndIfExpressionData? {
        return prepareData(element, enforceNonNullableTypeIfPossible = true)
    }

    override fun createQuickFix(
        element: KtIfExpression,
        context: FoldInitializerAndIfExpressionData,
    ): KotlinModCommandQuickFix<KtIfExpression> = object : KotlinModCommandQuickFix<KtIfExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("replace.if.with.elvis.operator")

        override fun applyFix(
            project: Project,
            element: KtIfExpression,
            updater: ModPsiUpdater,
        ) {
            val variableDeclaration = updater.getWritable(context.variableDeclaration)
            val initializer = updater.getWritable(context.initializer)
            val ifNullExpr = updater.getWritable(context.ifNullExpression)
            val typeChecked = updater.getWritable<KtTypeReference>(context.typeChecked)

            if (context.couldBeVal) {
                variableDeclaration.replaceVarWithVal()
            }

            val elvis = joinLines(
                element,
                variableDeclaration,
                initializer,
                ifNullExpr,
                typeChecked,
                context.variableTypeString,
            )

            elvis.right?.textOffset?.let { updater.moveCaretTo(it) }
        }
    }

    override fun getProblemDescription(element: KtIfExpression, context: FoldInitializerAndIfExpressionData) =
        KotlinBundle.message("if.null.return.break.foldable.to")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitIfExpression(expression: KtIfExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtIfExpression): List<TextRange> = ApplicabilityRanges.ifExpressionExcludingBranches(element)

    override fun isApplicableByPsi(element: KtIfExpression): Boolean {
        fun KtExpression.isElvisExpression(): Boolean = this is KtBinaryExpression && operationToken == KtTokens.ELVIS

        val prevStatement = (element.siblings(forward = false, withItself = false)
            .firstIsInstanceOrNull<KtExpression>() ?: return false) as? KtVariableDeclaration

        val initializer = prevStatement?.initializer ?: return false

        if (initializer.isMultiLine()) return false

        return !initializer.anyDescendantOfType<KtExpression> {
            it is KtThrowExpression || it is KtReturnExpression || it is KtBreakExpression ||
                    it is KtContinueExpression || it is KtIfExpression || it is KtWhenExpression ||
                    it is KtTryExpression || it is KtLambdaExpression || it.isElvisExpression()
        }
    }
}
