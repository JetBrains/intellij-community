// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.resolution.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.resolution.symbol
import org.jetbrains.kotlin.idea.base.analysis.api.utils.allOverriddenSymbolsWithSelf
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.asUnit
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.ApplicabilityRange
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.util.OperatorNameConventions

private val TO_STRING_CALLABLE_ID = CallableId(StandardClassIds.Any, OperatorNameConventions.TO_STRING)

internal class RemoveToStringInStringTemplateInspection : KotlinApplicableInspectionBase.Simple<KtDotQualifiedExpression, Unit>(),
                                                          CleanupLocalInspectionTool {

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getProblemDescription(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): String = KotlinBundle.message("remove.to.string.fix.text")

    override fun getApplicableRanges(element: KtDotQualifiedExpression): List<TextRange> =
        ApplicabilityRange.single(element) { element.selectorExpression }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        if (element.parent !is KtBlockStringTemplateEntry) return false
        if (element.receiverExpression is KtSuperExpression) return false
        val callExpression = element.selectorExpression as? KtCallExpression ?: return false
        val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        return referenceExpression.getReferencedNameAsName() == OperatorNameConventions.TO_STRING && callExpression.valueArguments.isEmpty()
    }

    override fun KaSession.prepareContext(element: KtDotQualifiedExpression): Unit? {
        val call = element.resolveToCall()?.successfulFunctionCallOrNull() ?: return null
        val allOverriddenSymbols = call.symbol.allOverriddenSymbolsWithSelf
        return allOverriddenSymbols.any { it.callableId == TO_STRING_CALLABLE_ID }
            .asUnit
    }

    override fun createQuickFix(
        element: KtDotQualifiedExpression,
        context: Unit,
    ): KotlinModCommandQuickFix<KtDotQualifiedExpression> = object : KotlinModCommandQuickFix<KtDotQualifiedExpression>() {

        override fun getFamilyName(): String =
            KotlinBundle.message("remove.to.string.fix.text")

        override fun applyFix(
            project: Project,
            element: KtDotQualifiedExpression,
            updater: ModPsiUpdater,
        ) {
            val receiverExpression = element.receiverExpression
            val templateEntry = element.parent as? KtBlockStringTemplateEntry
            if (receiverExpression is KtNameReferenceExpression &&
                templateEntry != null &&
                canPlaceAfterSimpleNameEntry(templateEntry.nextSibling)
            ) {
                val factory = KtPsiFactory(templateEntry.project)
                templateEntry.replace(factory.createSimpleNameStringTemplateEntry(receiverExpression.getReferencedName()))
            } else {
                element.replace(receiverExpression)
            }
        }
    }
}
