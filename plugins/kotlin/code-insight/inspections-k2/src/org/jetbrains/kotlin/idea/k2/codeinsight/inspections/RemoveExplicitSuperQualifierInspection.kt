// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.resolution.KaCallableMemberCall
import org.jetbrains.kotlin.analysis.api.resolution.singleCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.successfulCallOrNull
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.endOffset
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class RemoveExplicitSuperQualifierInspection :
    KotlinApplicableInspectionBase.Simple<KtSuperExpression, RemoveExplicitSuperQualifierInspection.Context>(), CleanupLocalInspectionTool {

    data class Context(val qualifiedExpression: KtQualifiedExpression)

    override fun getProblemDescription(element: KtSuperExpression, context: Context) =
        KotlinBundle.message("remove.explicit.supertype.qualification")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {
        override fun visitSuperExpression(expression: KtSuperExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicableRanges(element: KtSuperExpression): List<TextRange> =
        listOf(TextRange(
            element.instanceReference.endOffset,
            element.labelQualifier?.startOffset ?: element.endOffset
        ).shiftLeft(element.startOffset))

    override fun isApplicableByPsi(element: KtSuperExpression): Boolean =
        element.superTypeQualifier != null

    override fun KaSession.prepareContext(element: KtSuperExpression): Context? {
        if (element.superTypeQualifier == null) return null

        val qualifiedExpression = element.getQualifiedExpressionForReceiver() ?: return null
        val selector = qualifiedExpression.selectorExpression ?: return null

        // Check if the selector has a resolved call, probably
        val resolvedCall = selector.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>() ?: return null

        // Create a non-qualified super expression to check if it would resolve to the same call
        val nonQualifiedSuper = toNonQualified(element)
        val newQualifiedExpression = KtPsiFactory(element.project).createExpressionCodeFragment(nonQualifiedSuper.text + "." + selector.text, element).getContentElement() as KtQualifiedExpression


        // Check if the new expression would resolve to a valid call
        val newCallableId =
            analyze(newQualifiedExpression) {
                val callableMemberCall =
                    newQualifiedExpression.selectorExpression?.resolveToCall()?.singleCallOrNull<KaCallableMemberCall<*, *>>()
                callableMemberCall?.partiallyAppliedSymbol?.signature?.callableId
            }

        if (resolvedCall.partiallyAppliedSymbol.signature.callableId != newCallableId) return null

        return Context(qualifiedExpression)
    }

    override fun createQuickFix(
        element: KtSuperExpression,
        context: Context,
    ): KotlinModCommandQuickFix<KtSuperExpression> = object : KotlinModCommandQuickFix<KtSuperExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.explicit.supertype.qualification")

        override fun applyFix(
            project: Project,
            element: KtSuperExpression,
            updater: ModPsiUpdater,
        ) {
            element.replace(toNonQualified(element))
        }
    }

    private fun toNonQualified(superExpression: KtSuperExpression): KtSuperExpression {
        val psiFactory = KtPsiFactory(superExpression.project)
        val labelName = superExpression.getLabelNameAsName()
        return (if (labelName != null)
            psiFactory.createExpressionByPattern("super@$0", labelName.asString())
        else
            psiFactory.createExpression("super")) as KtSuperExpression
    }
}
