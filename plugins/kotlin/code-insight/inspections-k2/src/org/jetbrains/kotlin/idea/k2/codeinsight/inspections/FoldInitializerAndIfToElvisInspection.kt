// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.psi.textRangeIn
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeInsight.FoldInitializerAndIfExpressionData
import org.jetbrains.kotlin.idea.codeInsight.joinLines
import org.jetbrains.kotlin.idea.codeInsight.prepareData
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.*
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class FoldInitializerAndIfToElvisInspection :
    AbstractKotlinApplicableInspectionWithContext<KtIfExpression, FoldInitializerAndIfExpressionData>() {

    override fun getProblemHighlightType(element: KtIfExpression, context: FoldInitializerAndIfExpressionData): ProblemHighlightType {
        return when (element.condition) {
            is KtBinaryExpression -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            else -> ProblemHighlightType.INFORMATION
        }
    }

    context(KtAnalysisSession)
    override fun prepareContext(element: KtIfExpression): FoldInitializerAndIfExpressionData? {
        return prepareData(element)
    }

    override fun apply(element: KtIfExpression, context: FoldInitializerAndIfExpressionData, project: Project, updater: ModPsiUpdater) {
        val elvis = joinLines(
            element,
            updater.getWritable<KtVariableDeclaration>(context.variableDeclaration),
            updater.getWritable<KtExpression>(context.initializer),
            updater.getWritable<KtExpression>(context.ifNullExpression),
            updater.getWritable<KtTypeReference>(context.typeChecked),
            context.variableTypeString
        )

        elvis.right?.textOffset?.let { updater.moveCaretTo(it) }
    }

    override fun getProblemDescription(element: KtIfExpression, context: FoldInitializerAndIfExpressionData) =
        KotlinBundle.message("if.null.return.break.foldable.to")

    override fun getActionFamilyName() = KotlinBundle.message("replace.if.with.elvis.operator")

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitIfExpression(expression: KtIfExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtIfExpression> = applicabilityRange { ifExpression ->
        val rightOffset = ifExpression.rightParenthesis?.endOffset

        if (rightOffset == null) {
            ifExpression.ifKeyword.textRangeIn(ifExpression)
        } else {
            TextRange(ifExpression.ifKeyword.startOffset, rightOffset).shiftLeft(ifExpression.startOffset)
        }
    }

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
