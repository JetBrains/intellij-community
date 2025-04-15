// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections.substring

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.psi.*

internal class ReplaceSubstringWithIndexingOperationInspection : ReplaceSubstringInspection() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.callExpression ?: return false
        if (!isMethodCall(callExpression, "substring")) return false

        val arguments = callExpression.valueArguments
        if (arguments.size != 2) return false

        // Check if both arguments are constant expressions
        return arguments[0].getArgumentExpression() is KtConstantExpression && arguments[1].getArgumentExpression() is KtConstantExpression
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (!resolvesToMethod(element, "kotlin.text.substring")) return null
        val arguments = element.callExpression?.valueArguments ?: return null

        // Evaluate the arguments as constants
        val firstArg = arguments[0].getArgumentExpression() ?: return null
        val secondArg = arguments[1].getArgumentExpression() ?: return null

        val firstValue = firstArg.evaluate()?.value as? Int ?: return null
        val secondValue = secondArg.evaluate()?.value as? Int ?: return null

        if (secondValue != firstValue + 1) return null

        return Unit
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.substring.with.indexing.operation.display.name")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.substring.call.with.indexing.operation.call")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(project)
            val replacement = factory.createExpressionByPattern(
                "$0[$1]",
                element.receiverExpression,
                element.callExpression?.valueArguments?.getOrNull(0)?.getArgumentExpression() ?: return,
            )
            element.replace(replacement)
        }
    }
}