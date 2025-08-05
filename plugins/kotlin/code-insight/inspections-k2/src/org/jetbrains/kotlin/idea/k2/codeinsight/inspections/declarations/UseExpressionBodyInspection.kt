// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.declarations

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.components.computeMissingCases
import org.jetbrains.kotlin.analysis.api.components.expressionType
import org.jetbrains.kotlin.analysis.api.components.isNothingType
import org.jetbrains.kotlin.analysis.api.components.isUnitType
import org.jetbrains.kotlin.idea.base.psi.isOneLiner
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.isConvertableToExpressionBody
import org.jetbrains.kotlin.idea.codeinsight.utils.replaceWithExpressionBodyPreservingComments
import org.jetbrains.kotlin.idea.util.resultingWhens
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.startOffset


internal class UseExpressionBodyInspection :
    KotlinApplicableInspectionBase.Simple<KtDeclarationWithBody, UseExpressionBodyInspection.Context>() {

    data class Context(val subject: String, val highlightType: ProblemHighlightType, val requireType: Boolean = false)

    override fun getProblemDescription(
        element: KtDeclarationWithBody,
        context: Context,
    ): String = KotlinBundle.message("use.expression.body.instead.of.0", context.subject)

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

    override fun getApplicableRanges(element: KtDeclarationWithBody): List<TextRange> {
        fun KtExpression.toHighlight(): PsiElement? = when (this) {
            is KtReturnExpression -> returnKeyword
            is KtCallExpression -> calleeExpression
            is KtQualifiedExpression -> selectorExpression?.toHighlight()
            is KtObjectLiteralExpression -> objectDeclaration.getObjectKeyword()
            else -> this
        }

        return ApplicabilityRange.multiple(element) { declaration: KtDeclarationWithBody ->
            val bodyBlockExpression = declaration.bodyBlockExpression
            val toHighlightElement = bodyBlockExpression?.statements?.singleOrNull()?.toHighlight()
            val rangeElements = if (toHighlightElement == null) {
                listOf(bodyBlockExpression)
            } else {
                listOf(toHighlightElement, bodyBlockExpression.lBrace)
            }

            rangeElements.filterNotNull()
        }
    }

    override fun getProblemHighlightType(
        element: KtDeclarationWithBody, context: Context
    ): ProblemHighlightType = context.highlightType

    override fun KaSession.prepareContext(element: KtDeclarationWithBody): Context? {
        val valueStatementResult = element.findValueStatement() ?: return null
        val valueStatement = valueStatementResult.statement
        val requireType = valueStatement?.expressionType?.isNothingType == true
        return when {
            valueStatement !is KtReturnExpression -> Context(KotlinBundle.message("block.body"), INFORMATION)
            valueStatement.returnedExpression is KtWhenExpression -> Context(KotlinBundle.message("return.when"), INFORMATION)
            valueStatement.isOneLiner() -> Context(KotlinBundle.message("one.line.return"), GENERIC_ERROR_OR_WARNING)
            else -> Context(KotlinBundle.message("text.return"), INFORMATION)
        }.copy(requireType = requireType)
    }

    @JvmInline
    private value class ValueStatementResult(val statement: KtExpression?)

    context(_: KaSession)
    private fun KtDeclarationWithBody.findValueStatement(): ValueStatementResult? {
        val statements = bodyBlockExpression?.statements
        if (statements.isNullOrEmpty()) {
            return ValueStatementResult(statement = null)
        }

        val statement = statements.singleOrNull() ?: return null
        when (statement) {
            is KtReturnExpression -> {
                return ValueStatementResult(statement)
            }

            //TODO: IMO this is not good code, there should be a way to detect that KtExpression does not have value
            is KtDeclaration, is KtLoopExpression -> return null

            else -> { // assignment does not have value
                if (statement is KtBinaryExpression && statement.operationToken in KtTokens.ALL_ASSIGNMENTS) return null

                val expressionType = statement.expressionType ?: return null
                val isUnit = expressionType.isUnitType
                if (!isUnit && !expressionType.isNothingType) return null
                if (isUnit) {
                    if (statement.hasResultingIfWithoutElse()) {
                        return null
                    }
                    val resultingWhens = statement.resultingWhens()
                    if (resultingWhens.any { it.elseExpression == null && it.computeMissingCases().isNotEmpty() }) {
                        return null
                    }
                }

                return ValueStatementResult(statement)
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
    ): KotlinModCommandQuickFix<KtDeclarationWithBody> = object : KotlinModCommandQuickFix<KtDeclarationWithBody>() {

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