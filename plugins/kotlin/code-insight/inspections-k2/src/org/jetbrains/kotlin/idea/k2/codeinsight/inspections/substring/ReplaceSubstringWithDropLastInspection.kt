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
import org.jetbrains.kotlin.idea.codeinsights.impl.base.isSimplifiableTo
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*

internal class ReplaceSubstringWithDropLastInspection : ReplaceSubstringInspection() {
    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): KtVisitor<*, *> = dotQualifiedExpressionVisitor {
        visitTargetElement(it, holder, isOnTheFly)
    }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        val callExpression = element.callExpression ?: return false
        val arguments = callExpression.valueArguments

        if (!isSubstringFromZero(callExpression)) return false

        // Check if the second argument is a binary expression with a minus operator
        val secondArg = arguments[1].getArgumentExpression() as? KtBinaryExpression ?: return false
        if (secondArg.operationToken != KtTokens.MINUS) return false
        if (secondArg.right == null) return false


        // Check if left side of binary expression is length access on the same receiver
        val left = secondArg.left as? KtDotQualifiedExpression ?: return false

        val expectedReceiver = element.receiverExpression
        if (!left.receiverExpression.isSimplifiableTo(expectedReceiver)) return false

        val selector = left.selectorExpression as? KtNameReferenceExpression ?: return false
        return selector.getReferencedName() == "length"
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (!resolvesToMethod(element, "kotlin.text.substring")) return null
        if (!isFirstArgumentZero(element)) return null

        if (!element.receiverExpression.isPure()) return null
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