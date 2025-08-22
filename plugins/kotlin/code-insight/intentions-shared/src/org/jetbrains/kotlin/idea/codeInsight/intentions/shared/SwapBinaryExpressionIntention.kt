// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.util.PsiPrecedences
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.types.expressions.OperatorConventions

internal class SwapBinaryExpressionIntention : KotlinPsiUpdateModCommandAction.ClassBased<KtBinaryExpression, Unit>(KtBinaryExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("flip.binary.expression")

    override fun getPresentation(context: ActionContext, element: KtBinaryExpression): Presentation? {
        val opRef = element.operationReference
        if (!opRef.textRange.containsOffset(context.offset)) return null

        if (leftSubject(element) == null || rightSubject(element) == null) {
            return null
        }

        val operationToken = element.operationToken
        val operationTokenText = opRef.text
        return if (operationToken in SUPPORTED_OPERATIONS
            || operationToken == IDENTIFIER && operationTokenText in SUPPORTED_OPERATION_NAMES
        ) {
            Presentation.of(KotlinBundle.message("flip.0", operationTokenText))
                .withPriority(PriorityAction.Priority.LOW)
        } else {
            null
        }
    }

    override fun invoke(actionContext: ActionContext, element: KtBinaryExpression, elementContext: Unit, updater: ModPsiUpdater) {
        // Have to use text here to preserve names like "plus"
        val convertedOperator = when (val operator = element.operationReference.text!!) {
            ">" -> "<"
            "<" -> ">"
            "<=" -> ">="
            ">=" -> "<="
            else -> operator
        }

        val left = leftSubject(element) ?: return
        val right = rightSubject(element) ?: return
        val rightCopy = right.copied()
        val leftCopy = left.copied()
        left.replace(rightCopy)
        right.replace(leftCopy)
        element.replace(KtPsiFactory(element.project).createExpressionByPattern("$0 $convertedOperator $1", element.left!!, element.right!!))
    }

    private fun leftSubject(element: KtBinaryExpression): KtExpression? =
        firstDescendantOfTighterPrecedence(element.left, PsiPrecedences.getPrecedence(element), KtBinaryExpression::getRight)

    private fun rightSubject(element: KtBinaryExpression): KtExpression? =
        firstDescendantOfTighterPrecedence(element.right, PsiPrecedences.getPrecedence(element), KtBinaryExpression::getLeft)

    private fun firstDescendantOfTighterPrecedence(
        expression: KtExpression?,
        precedence: Int,
        getChild: KtBinaryExpression.() -> KtExpression?
    ): KtExpression? {
        if (expression is KtBinaryExpression) {
            val expressionPrecedence = PsiPrecedences.getPrecedence(expression)
            if (!PsiPrecedences.isTighter(expressionPrecedence, precedence)) {
                return firstDescendantOfTighterPrecedence(expression.getChild(), precedence, getChild)
            }
        }

        return expression
    }

    override fun KaSession.prepareContext(element: KtBinaryExpression) {
    }
}

private val SUPPORTED_OPERATIONS: Set<KtSingleValueToken> by lazy {
    setOf(PLUS, MUL, OROR, ANDAND, EQEQ, EXCLEQ, EQEQEQ, EXCLEQEQEQ, GT, LT, GTEQ, LTEQ)
}

private val SUPPORTED_OPERATION_NAMES: Set<String> by lazy {
    SUPPORTED_OPERATIONS.asSequence().mapNotNull { OperatorConventions.BINARY_OPERATION_NAMES[it]?.asString() }.toSet() +
            setOf("xor", "or", "and", "equals")
}
