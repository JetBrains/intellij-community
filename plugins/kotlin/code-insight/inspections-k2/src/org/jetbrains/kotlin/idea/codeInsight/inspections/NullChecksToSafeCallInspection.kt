// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.isMarkedNullable
import org.jetbrains.kotlin.analysis.api.components.isStableForSmartCasting
import org.jetbrains.kotlin.analysis.api.components.resolveToCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.symbols.receiverType
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.lexer.KtToken
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtThisExpression
import org.jetbrains.kotlin.psi.KtVisitor
import org.jetbrains.kotlin.psi.binaryExpressionVisitor
import org.jetbrains.kotlin.psi.buildExpression

internal class NullChecksToSafeCallInspection : KotlinApplicableInspectionBase.Simple<KtBinaryExpression, Unit>() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): KtVisitor<*, *> =
        binaryExpressionVisitor { visitTargetElement(it, holder, isOnTheFly) }

    override fun getProblemDescription(element: KtBinaryExpression, context: Unit): @InspectionMessage String =
        KotlinBundle.message("null.checks.replaceable.with.safe.calls")

    override fun isApplicableByPsi(element: KtBinaryExpression): Boolean =
        collectNullCheckExpressions(element) != null

    override fun KaSession.prepareContext(element: KtBinaryExpression): Unit? =
        isNullChecksToSafeCallFixAvailable(element).asUnit

    override fun createQuickFix(
        element: KtBinaryExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtBinaryExpression> = object : KotlinModCommandQuickFix<KtBinaryExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("null.checks.to.safe.call.check.fix.text")

        override fun applyFix(project: Project, element: KtBinaryExpression, updater: ModPsiUpdater) {
            replaceNullChecksWithSafeCall(element, project)
        }
    }
}

private fun replaceNullChecksWithSafeCall(element: KtBinaryExpression, project: Project) {
    val (lte, rte, isAnd) = collectNullCheckExpressions(element) ?: return
    val parent = element.parent

    element.replaced(KtPsiFactory(project).buildExpression {
        appendExpression(lte)
        appendFixedText("?.")
        appendExpression(rte.selectorExpression)
        appendFixedText(if (isAnd) "!= null" else "== null")
    })

    val parentBinaryExpression = parent as? KtBinaryExpression ?: return
    val isNullChecksToSafeCallFixAvailable = analyze(parentBinaryExpression) { isNullChecksToSafeCallFixAvailable(parentBinaryExpression) }
    if (isNullChecksToSafeCallFixAvailable) {
        replaceNullChecksWithSafeCall(parentBinaryExpression, project)
    }
}

context(_: KaSession)
private fun isNullChecksToSafeCallFixAvailable(expression: KtBinaryExpression): Boolean {
    fun String.afterIgnoreCalls() = replace("?.", ".")

    val (lte, rte) = collectNullCheckExpressions(expression) ?: return false
    if (!hasStableSmartCast(rte.receiverExpression)) return false

    val resolvedCall = rte.resolveToCall()?.successfulCallOrNull<KaCallableMemberCall<*, *>>() ?: return false
    val hasNullableExtensionReceiver = resolvedCall.symbol.receiverType?.isMarkedNullable == true

    return !hasNullableExtensionReceiver && rte.receiverExpression.text.afterIgnoreCalls() == lte.text.afterIgnoreCalls()
}

private fun collectNullCheckExpressions(expression: KtBinaryExpression): Triple<KtExpression, KtQualifiedExpression, Boolean>? {
    val isAnd = when (expression.operationToken) {
        KtTokens.ANDAND -> true
        KtTokens.OROR -> false
        else -> return null
    }
    val lhs = expression.left as? KtBinaryExpression ?: return null
    val rhs = expression.right as? KtBinaryExpression ?: return null

    val expectedOperation = if (isAnd) KtTokens.EXCLEQ else KtTokens.EQEQ
    val lte = lhs.getNullTestableExpression(expectedOperation) ?: return null
    val rte = rhs.getNullTestableExpression(expectedOperation) as? KtQualifiedExpression ?: return null

    return Triple(lte, rte, isAnd)
}

private fun KtBinaryExpression.getNullTestableExpression(expectedOperation: KtToken): KtExpression? {
    if (operationToken != expectedOperation) return null
    val lhs = left ?: return null
    val rhs = right ?: return null
    if (KtPsiUtil.isNullConstant(lhs)) return rhs
    if (KtPsiUtil.isNullConstant(rhs)) return lhs
    return null
}

@OptIn(KaExperimentalApi::class)
context(_: KaSession)
private fun hasStableSmartCast(expression: KtExpression): Boolean {
    val expressionToCheck = when (expression) {
        is KtThisExpression -> expression.instanceReference
        else -> expression
    }
    return expressionToCheck.isStableForSmartCasting
}
