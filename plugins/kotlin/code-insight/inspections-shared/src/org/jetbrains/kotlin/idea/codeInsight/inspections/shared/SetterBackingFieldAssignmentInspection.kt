// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiWhiteSpace
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.base.util.reformatted
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.isBackingFieldRequired
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.siblings
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class SetterBackingFieldAssignmentInspection : KotlinApplicableInspectionBase.Simple<KtPropertyAccessor, Unit>(),
                                                        CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = propertyAccessorVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtPropertyAccessor): Boolean {
        if (!element.isSetter) return false
        val bodyExpression = element.bodyBlockExpression ?: return false
        if (bodyExpression.firstStatement is KtThrowExpression) return false
        return element.valueParameters.singleOrNull() != null
    }

    override fun getApplicableRanges(element: KtPropertyAccessor): List<TextRange> {
        val name = element.namePlaceholder
        val right = element.parameterList?.rightParenthesis ?: name
        val range = TextRange(name.startOffset, right.endOffset).shiftLeft(element.startOffset)
        return listOf(range)
    }

    override fun KaSession.prepareContext(element: KtPropertyAccessor): Unit? {
        val property = element.property
        if (!isBackingFieldRequired(property)) return null
        val parameter = element.valueParameters.singleOrNull() ?: return null
        val bodyExpression = element.bodyBlockExpression ?: return null

        val hasAllowedUsage = bodyExpression.anyDescendantOfType<KtExpression> { expr ->
            when (expr) {
                is KtBinaryExpression ->
                    isBackingFieldReference(expr.left, property) && expr.operationToken in assignmentOperators

                is KtUnaryExpression ->
                    isBackingFieldReference(expr.baseExpression, property) && expr.operationToken in incrementAndDecrementOperators

                is KtCallExpression ->
                    expr.valueArguments.any { arg ->
                        val argumentSymbol = arg.getArgumentExpression()
                            ?.mainReference
                            ?.resolveToSymbol()

                        argumentSymbol != null && argumentSymbol == parameter.symbol
                    }

                else -> false
            }
        }

        return hasAllowedUsage.not().asUnit
    }

    override fun getProblemDescription(
        element: KtPropertyAccessor,
        context: Unit,
    ): @InspectionMessage String = KotlinBundle.message("existing.backing.field.is.not.assigned.by.the.setter")

    override fun createQuickFix(
        element: KtPropertyAccessor,
        context: Unit,
    ): KotlinModCommandQuickFix<KtPropertyAccessor> = object : KotlinModCommandQuickFix<KtPropertyAccessor>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("assign.backing.field.fix.text")

        override fun applyFix(
            project: Project,
            element: KtPropertyAccessor,
            updater: ModPsiUpdater,
        ) {
            val parameter = element.valueParameters.firstOrNull() ?: return
            val bodyExpression = element.bodyBlockExpression ?: return

            bodyExpression.removeRedundantWhiteSpace()

            val psiFactory = KtPsiFactory(project)
            val assignment = psiFactory.createExpression("field = ${parameter.name}")
            val lastStatement = bodyExpression.statements.lastOrNull()
            if (lastStatement != null) {
                bodyExpression.addAfter(assignment, lastStatement)
                bodyExpression.addAfter(psiFactory.createNewLine(), lastStatement)
                bodyExpression.reformatted()
            } else {
                bodyExpression.addBefore(assignment, bodyExpression.rBrace)
            }
        }
    }
}

private val assignmentOperators: List<KtSingleValueToken> = listOf(
    KtTokens.EQ,
    KtTokens.PLUSEQ,
    KtTokens.MINUSEQ,
    KtTokens.MULTEQ,
    KtTokens.DIVEQ,
    KtTokens.PERCEQ,
)
private val incrementAndDecrementOperators: List<KtSingleValueToken> = listOf(
    KtTokens.PLUSPLUS,
    KtTokens.MINUSMINUS,
)

private fun KtBlockExpression.removeRedundantWhiteSpace() {
    lBrace
        ?.siblings(withItself = false)
        ?.takeWhile { it != rBrace }
        ?.singleOrNull { it is PsiWhiteSpace }
        ?.also { it.delete() }
}
