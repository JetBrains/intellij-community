// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.substring

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.psi.*

internal class ReplaceSubstringWithDropLastInspection : ReplaceSubstringInspection() {
    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.callExpression ?: return false
        if (!isSubstringFromZero(callExpression)) return false

        val secondArg = getBinaryExpressionWithMinus(callExpression) ?: return false
        return isAccessedOnSameReceiver(secondArg, element.receiverExpression)
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (!prepareContextBase(element)) return null
        return Unit
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.substring.with.drop.last.display.name")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.substring.call.with.droplast.call")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(project)
            val replacement = factory.createExpressionByPattern(
                "$0.dropLast($1)",
                element.receiverExpression,
                (element.callExpression?.valueArguments?.getOrNull(1)?.getArgumentExpression() as? KtBinaryExpression)?.right ?: return,
            )
            element.replace(replacement)
        }
    }
}