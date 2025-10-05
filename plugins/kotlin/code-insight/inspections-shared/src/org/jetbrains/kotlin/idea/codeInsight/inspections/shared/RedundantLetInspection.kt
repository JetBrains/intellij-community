// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.builtins.StandardNames
import org.jetbrains.kotlin.idea.base.psi.isMultiLine
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.StandardKotlinNames
import org.jetbrains.kotlin.idea.codeinsight.utils.isCallingAnyOf
import org.jetbrains.kotlin.idea.codeinsight.utils.isLetCallRedundant
import org.jetbrains.kotlin.idea.codeinsight.utils.removeRedundantLetCall
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelector

internal sealed class RedundantLetInspection :
    KotlinApplicableInspectionBase.Simple<KtCallExpression, Unit>() {

    final override fun getProblemDescription(
        element: KtCallExpression,
        context: Unit,
    ): String = KotlinBundle.message("redundant.let.call.could.be.removed")

    final override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun KaSession.prepareContext(element: KtCallExpression): Unit? {
        if (!element.isCallingAnyOf(StandardKotlinNames.let)) return null
        val lambdaExpression = element.lambdaArguments.firstOrNull()?.getLambdaExpression() ?: return null
        val parameterName = lambdaExpression.getParameterName() ?: return null
        val bodyExpression = lambdaExpression.bodyExpression?.children?.singleOrNull() ?: return null

        return isApplicable(
            element,
            bodyExpression,
            lambdaExpression,
            parameterName,
        ).asUnit
    }

    context(_: KaSession)
    protected abstract fun isApplicable(
        element: KtCallExpression,
        bodyExpression: PsiElement,
        lambdaExpression: KtLambdaExpression,
        parameterName: String,
    ): Boolean

    final override fun createQuickFix(
        element: KtCallExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtCallExpression> = object : KotlinModCommandQuickFix<KtCallExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.let.call")

        override fun applyFix(
            project: Project,
            element: KtCallExpression,
            updater: ModPsiUpdater,
        ) {
            removeRedundantLetCall(element) {
                updater.moveCaretTo(it)
            }
        }
    }

    final override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitorVoid = callExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }
}

internal class SimpleRedundantLetInspection : RedundantLetInspection() {

    context(_: KaSession)
    override fun isApplicable(
        element: KtCallExpression,
        bodyExpression: PsiElement,
        lambdaExpression: KtLambdaExpression,
        parameterName: String,
    ): Boolean = when (bodyExpression) {
        is KtDotQualifiedExpression -> bodyExpression.isLetCallRedundant(parameterName)
        is KtSimpleNameExpression -> bodyExpression.text == parameterName
        else -> false
    }
}

internal class ComplexRedundantLetInspection : RedundantLetInspection() {

    context(_: KaSession)
    override fun isApplicable(
        element: KtCallExpression,
        bodyExpression: PsiElement,
        lambdaExpression: KtLambdaExpression,
        parameterName: String,
    ): Boolean = isLetCallRedundant(element, bodyExpression, lambdaExpression, parameterName)

    override fun getProblemHighlightType(
        element: KtCallExpression,
        context: Unit,
    ): ProblemHighlightType =
        if (isSingleLine(element)) ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        else ProblemHighlightType.INFORMATION
}


private fun KtLambdaExpression.getParameterName(): String? {
    val parameters = valueParameters
    if (parameters.size > 1) return null
    return if (parameters.size == 1) parameters[0].text else StandardNames.IMPLICIT_LAMBDA_PARAMETER_NAME.identifier
}

private fun isSingleLine(element: KtCallExpression): Boolean {
    val qualifiedExpression = element.getQualifiedExpressionForSelector() ?: return true
    var receiver = qualifiedExpression.receiverExpression
    if (receiver.isMultiLine()) return false
    var count = 1
    while (true) {
        if (count > 2) return false
        receiver = (receiver as? KtQualifiedExpression)?.receiverExpression ?: break
        count++
    }
    return true
}
