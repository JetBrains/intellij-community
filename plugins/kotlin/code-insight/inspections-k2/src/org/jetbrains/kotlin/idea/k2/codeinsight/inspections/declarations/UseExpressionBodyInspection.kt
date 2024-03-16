// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.codeinsight.utils.isConvertableToExpressionBody
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceWithExpressionBodyPreservingComments
import org.jetbrains.kotlin.idea.util.resultingWhens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset


internal class UseExpressionBodyInspection :
    AbstractKotlinApplicableInspectionWithContext<KtDeclarationWithBody, UseExpressionBodyInspection.Context>() {

    data class Context(val subject: String, val highlightType: ProblemHighlightType, val requireType: Boolean = false)

    override fun getProblemDescription(
        element: KtDeclarationWithBody, context: Context
    ): @InspectionMessage String = KotlinBundle.message("use.expression.body.instead.of.0", context.subject)

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitNamedFunction(function: KtNamedFunction) {
            visitTargetElement(function, holder, isOnTheFly)
        }

        override fun visitPropertyAccessor(accessor: KtPropertyAccessor) {
            visitTargetElement(accessor, holder, isOnTheFly)
        }
    }

    override fun isApplicableByPsi(element: KtDeclarationWithBody): Boolean = element.isConvertableToExpressionBody()

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDeclarationWithBody> = applicabilityTarget { declaration ->
        val bodyBlockExpression = declaration.bodyBlockExpression

        fun KtExpression.toHighlight(): PsiElement? = when (this) {
            is KtReturnExpression -> returnKeyword
            is KtCallExpression -> calleeExpression
            is KtQualifiedExpression -> selectorExpression?.toHighlight()
            is KtObjectLiteralExpression -> objectDeclaration.getObjectKeyword()
            else -> this
        }

        bodyBlockExpression?.statements?.singleOrNull()?.toHighlight() ?: bodyBlockExpression
    }

    override fun getProblemHighlightType(
        element: KtDeclarationWithBody, context: Context
    ): ProblemHighlightType = context.highlightType

    context(KtAnalysisSession)
    override fun prepareContext(declaration: KtDeclarationWithBody): Context? {
        val valueStatement = declaration.findValueStatement() ?: return null
        val requireType = valueStatement.getKtType()?.isNothing == true
        return when {
            valueStatement !is KtReturnExpression -> Context(KotlinBundle.message("block.body"), INFORMATION)
            valueStatement.returnedExpression is KtWhenExpression -> Context(KotlinBundle.message("return.when"), INFORMATION)
            valueStatement.isOneLiner() -> Context(KotlinBundle.message("one.line.return"), GENERIC_ERROR_OR_WARNING)
            else -> Context(KotlinBundle.message("text.return"), INFORMATION)
        }.copy(requireType = requireType)
    }

    context(KtAnalysisSession)
    private fun KtDeclarationWithBody.findValueStatement(): KtExpression? {
        val statements = bodyBlockExpression?.statements
        if (statements == null || statements.isEmpty()) {
            return KtPsiFactory(project).createExpression("Unit")
        }
        val statement = statements.singleOrNull() ?: return null
        when (statement) {
            is KtReturnExpression -> {
                return statement
            }

            //TODO: IMO this is not good code, there should be a way to detect that KtExpression does not have value
            is KtDeclaration, is KtLoopExpression -> return null

            else -> { // assignment does not have value
                if (statement is KtBinaryExpression && statement.operationToken in KtTokens.ALL_ASSIGNMENTS) return null

                val expressionType = statement.getKtType() ?: return null
                val isUnit = expressionType.isUnit
                if (!isUnit && !expressionType.isNothing) return null
                if (isUnit) {
                    if (statement.hasResultingIfWithoutElse()) {
                        return null
                    }
                    val resultingWhens = statement.resultingWhens()
                    if (resultingWhens.any { it.elseExpression == null && it.getMissingCases().isNotEmpty() }) {
                        return null
                    }
                }
                return statement
            }
        }
    }

    private fun KtExpression?.hasResultingIfWithoutElse(): Boolean = when (this) {
        is KtIfExpression -> `else` == null || then.hasResultingIfWithoutElse() || `else`.hasResultingIfWithoutElse()
        is KtWhenExpression -> entries.any { it.expression.hasResultingIfWithoutElse() }
        is KtBinaryExpression -> left.hasResultingIfWithoutElse() || right.hasResultingIfWithoutElse()
        is KtUnaryExpression -> baseExpression.hasResultingIfWithoutElse()
        is KtBlockExpression -> statements.lastOrNull().hasResultingIfWithoutElse()
        else -> false
    }

    override fun createQuickFix(
        element: KtDeclarationWithBody,
        context: Context,
    ) = object : KotlinModCommandQuickFix<KtDeclarationWithBody>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("convert.to.expression.body.fix.text")

        override fun applyFix(
            project: Project,
            element: KtDeclarationWithBody,
            updater: ModPsiUpdater,
        ) {
            val newBody = element.replaceWithExpressionBodyPreservingComments(context.requireType)

            val namedFunction = newBody.parent as? KtNamedFunction
            val typeReference = namedFunction?.typeReference
            if (typeReference != null) {
                val endOffset = typeReference.endOffset
                val colon = namedFunction.colon
                if (colon != null) {
                    updater.select(TextRange(colon.startOffset, endOffset))
                    updater.moveCaretTo(endOffset)
                }
            } else {
                updater.moveCaretTo(newBody.startOffset)
            }
        }
    }
}