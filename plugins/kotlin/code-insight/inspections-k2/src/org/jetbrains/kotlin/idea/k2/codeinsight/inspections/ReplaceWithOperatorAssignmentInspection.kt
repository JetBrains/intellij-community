// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.tree.IElementType
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtSingleValueToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*

internal class ReplaceWithOperatorAssignmentInspection :
    KotlinApplicableInspectionBase.Simple<KtBinaryExpression, ReplaceWithOperatorAssignmentInspection.Context>() {

    data class Context(
        val operatorAssignment: KtBinaryExpression,
        val problemHighlightType: ProblemHighlightType,
    )

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = binaryExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun getProblemDescription(
        element: KtBinaryExpression,
        context: Context,
    ): @InspectionMessage String = KotlinBundle.message("replaceable.with.operator.assignment")

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean {
        if (element.operationToken != KtTokens.EQ) return false
        if (element.left == null) return false
        val right = element.right as? KtBinaryExpression ?: return false
        return right.left != null && right.right != null
    }

    @OptIn(KaExperimentalApi::class)
    override fun KaSession.prepareContext(element: KtBinaryExpression): Context? {
        val left = element.left ?: return null
        val right = element.right as? KtBinaryExpression ?: return null

        if (!checkExpressionRepeat(left, right)) return null

        val operatorAssignment = buildOperatorAssignment(element) ?: return null

        if (operatorAssignment.operationReference.mainReference.resolveToSymbol() == null) return null

        val problemHighlightType = getProblemHighlightType(element)

        return Context(
            operatorAssignment,
            problemHighlightType,
        )
    }

    override fun getProblemHighlightType(
        element: KtBinaryExpression,
        context: Context,
    ): ProblemHighlightType = context.problemHighlightType

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.with.operator.assignment")

        override fun getName(): String = KotlinBundle.message(
            "replace.with.0",
            (context.operatorAssignment.operationToken as KtSingleValueToken).value,
        )

        override fun applyFix(
            project: Project,
            element: KtBinaryExpression,
            updater: ModPsiUpdater,
        ) {
            element.replace(context.operatorAssignment)
        }
    }
}

private fun KaSession.getProblemHighlightType(element: KtBinaryExpression): ProblemHighlightType {
    val leftType = (element.left as? KtNameReferenceExpression)?.expressionType as? KaClassType
    return when {
        leftType?.isReadOnlyCollectionOrMap() == true -> ProblemHighlightType.INFORMATION
        else -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    }
}

private fun KaClassType.isReadOnlyCollectionOrMap(): Boolean =
    classId in listOf(StandardClassIds.List, StandardClassIds.Set, StandardClassIds.Map)

private fun KaSession.checkExpressionRepeat(
    variableExpression: KtExpression,
    expression: KtBinaryExpression,
): Boolean {
    val isPrimitiveOperation = isPrimitiveOperation(expression)

    val operationToken = expression.operationToken
    val expressionLeft = expression.left
    val expressionRight = expression.right
    return when {
        expressionLeft?.isSemanticMatch(variableExpression) == true -> {
            isArithmeticOperation(operationToken)
        }

        expressionRight?.isSemanticMatch(variableExpression) == true -> {
            isPrimitiveOperation && isCommutative(operationToken)
        }

        expressionLeft is KtBinaryExpression -> {
            val sameCommutativeOperation = expressionLeft.operationToken == operationToken && isCommutative(operationToken)
            isPrimitiveOperation && sameCommutativeOperation && checkExpressionRepeat(
                variableExpression,
                expressionLeft,
            )
        }

        else -> {
            false
        }
    }
}

private fun KaSession.isPrimitiveOperation(expression: KtBinaryExpression): Boolean {
    val operationSymbol = expression.operationReference
        .mainReference
        .resolveToSymbol()
        ?.containingSymbol as? KaClassSymbol ?: return false

    return operationSymbol.defaultType.isPrimitive
}

private fun isCommutative(operationToken: IElementType): Boolean =
    operationToken == KtTokens.PLUS || operationToken == KtTokens.MUL

private fun isArithmeticOperation(operationToken: IElementType): Boolean =
    operationToken == KtTokens.PLUS ||
            operationToken == KtTokens.MINUS ||
            operationToken == KtTokens.MUL ||
            operationToken == KtTokens.DIV ||
            operationToken == KtTokens.PERC

private fun KaSession.buildOperatorAssignment(element: KtBinaryExpression): KtBinaryExpression? {
    val variableExpression = element.left ?: return null
    val assignedExpression = element.right as? KtBinaryExpression ?: return null

    val replacement = buildOperatorAssignmentText(variableExpression, assignedExpression, "")
    val codeFragment = KtPsiFactory(element.project).createExpressionCodeFragment(replacement, element)
    return codeFragment.getContentElement() as? KtBinaryExpression
}

private tailrec fun KaSession.buildOperatorAssignmentText(
    variableExpression: KtExpression,
    expression: KtBinaryExpression,
    tail: String,
): String {
    val operationText = expression.operationReference.text
    val variableName = variableExpression.text

    fun String.appendTail(): String = if (tail.isEmpty()) this else "$this $tail"

    return when {
        expression.left?.isSemanticMatch(variableExpression) == true ->
            "$variableName $operationText= ${expression.right!!.text}".appendTail()

        expression.right?.isSemanticMatch(variableExpression) == true ->
            "$variableName $operationText= ${expression.left!!.text}".appendTail()

        expression.left is KtBinaryExpression ->
            buildOperatorAssignmentText(
                variableExpression,
                expression.left as KtBinaryExpression,
                "$operationText ${expression.right!!.text}".appendTail()
            )

        else ->
            tail
    }
}
