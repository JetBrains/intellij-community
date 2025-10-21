// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.substring

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.util.elementType
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.KaCall
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespace

internal class ReplaceSubstringWithTakeInspection : ReplaceSubstringInspection() {
    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.callExpression ?: return false
        if (!isSubstringFromZero(callExpression)) return false

        // Don't trigger if ReplaceSubstringWithDropLastInspection would be triggered
        val secondArg = getBinaryExpressionWithMinus(callExpression) ?: return true
        return !isAccessedOnSameReceiver(secondArg, element.receiverExpression)
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (!prepareContextBase(element)) return null
        if (!isFirstArgumentZero(element)) return null
        val leftType = element.receiverExpression.expressionType
        val rightType = getNextCallReceiverType(element)
        if (leftType != null && rightType != null && !leftType.isSubtypeOf(rightType)) return null
        return Unit
    }

    private fun KaSession.getNextCallReceiverType(element: KtDotQualifiedExpression): KaType? {
        // take() consumes `CharSequence`/`String` and returns `CharSequence`/`String` as well;
        // we should not break the call chain if the following call receiver is not `CharSequence`/`String`
        val next =
            element.getNextSiblingIgnoringWhitespace()?.takeIf { it.elementType == KtTokens.DOT } ?: return null
        val nextNext = next.getNextSiblingIgnoringWhitespace() as? KtCallExpression ?: return null
        val call =
            nextNext.resolveToCall()?.successfulCallOrNull<KaCall>() as? KaCallableMemberCall<*, *>
        return call?.symbol?.receiverParameter?.returnType
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.substring.with.take.display.name")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.substring.call.with.take.call")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(project)
            val secondArgument = element.callExpression?.valueArguments?.getOrNull(1)?.getArgumentExpression() ?: return
            val replacement = factory.createExpressionByPattern(
                "$0.take($1)",
                element.receiverExpression,
                secondArgument,
            )
            element.replace(replacement)
        }
    }
}