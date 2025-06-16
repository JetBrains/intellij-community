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
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.isPure
import org.jetbrains.kotlin.psi.*

internal class ReplaceSubstringWithSubstringAfterInspection : ReplaceSubstringInspection() {
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
        if (arguments.size != 1) return false

        // Check if the argument is an indexOf call on the same receiver
        val arg = arguments[0].getArgumentExpression() ?: return false
        return arg is KtDotQualifiedExpression && isMethodCall(arg.callExpression, "indexOf")
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (!resolvesToMethod(element, "kotlin.text.substring")) return null

        val arguments = element.callExpression?.valueArguments ?: return null
        if (arguments.size != 1) return null
        val arg = arguments[0].getArgumentExpression() ?: return null

        if (!isIndexOfCall(arg, element.receiverExpression)) return null
        if (!element.receiverExpression.isPure()) return null
        return Unit
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.substring.with.substring.after.display.name")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.substring.call.with.substringafter.call")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(project)
            val expression = element.callExpression?.valueArguments?.get(0)?.getArgumentExpression() as? KtDotQualifiedExpression
            val indexOfArg = expression?.callExpression?.valueArguments?.get(0)?.getArgumentExpression() ?: return

            val replacement = factory.createExpressionByPattern(
                "$0.substringAfter($1)",
                element.receiverExpression,
                indexOfArg
            )
            element.replace(replacement)
        }
    }
}

internal class ReplaceSubstringWithSubstringBeforeInspection : ReplaceSubstringInspection() {
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

        // Check if the second argument is an indexOf call on the same receiver
        val secondArg = arguments[1].getArgumentExpression() ?: return false
        return secondArg is KtDotQualifiedExpression &&
                (secondArg.callExpression?.calleeExpression as? KtNameReferenceExpression)?.getReferencedName() == "indexOf"
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        if (!resolvesToMethod(element, "kotlin.text.substring")) return null
        val arguments = element.callExpression?.valueArguments ?: return null
        if (arguments.size != 2) return null

        if (!isFirstArgumentZero(element)) return null
        val secondArg = arguments[1].getArgumentExpression() ?: return null

        if (!isIndexOfCall(secondArg, element.receiverExpression)) return null
        if (!element.receiverExpression.isPure()) return null
        return Unit
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): @InspectionMessage String =
        KotlinBundle.message("inspection.replace.substring.with.substring.before.display.name")

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {
        override fun getFamilyName(): @IntentionFamilyName String =
            KotlinBundle.message("replace.substring.call.with.substringbefore.call")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val factory = KtPsiFactory(project)
            val expression = element.callExpression?.valueArguments?.get(1)?.getArgumentExpression() as? KtDotQualifiedExpression
            val indexOfArg = expression?.callExpression?.valueArguments?.get(0)?.getArgumentExpression() ?: return

            val replacement = factory.createExpressionByPattern(
                "$0.substringBefore($1)",
                element.receiverExpression,
                indexOfArg
            )
            element.replace(replacement)
        }
    }
}