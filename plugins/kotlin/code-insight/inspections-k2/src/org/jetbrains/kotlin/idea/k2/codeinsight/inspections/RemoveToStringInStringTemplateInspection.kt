// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.refactoring.suggested.startOffset
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.successfulFunctionCallOrNull
import org.jetbrains.kotlin.analysis.api.calls.symbol
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRanges
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.canPlaceAfterSimpleNameEntry
import org.jetbrains.kotlin.util.OperatorNameConventions

private val TO_STRING_CALLABLE_ID = CallableId(StandardClassIds.Any, OperatorNameConventions.TO_STRING)

internal class RemoveToStringInStringTemplateInspection :
    AbstractKotlinApplicableInspection<KtDotQualifiedExpression>(),
    CleanupLocalInspectionTool {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return object : KtVisitorVoid() {
            override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression) {
                visitTargetElement(expression, holder, isOnTheFly)
            }
        }
    }
    override fun getProblemDescription(element: KtDotQualifiedExpression): String = KotlinBundle.message("remove.to.string.fix.text")
    override fun getActionFamilyName(): String = KotlinBundle.message("remove.to.string.fix.text")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtDotQualifiedExpression> =
        applicabilityRanges { dotQualifiedExpression: KtDotQualifiedExpression ->
            val selectorExpression = dotQualifiedExpression.selectorExpression ?: return@applicabilityRanges emptyList()
            listOf(selectorExpression.textRange.shiftLeft(dotQualifiedExpression.startOffset))
        }

    override fun isApplicableByPsi(element: KtDotQualifiedExpression): Boolean {
        if (element.parent !is KtBlockStringTemplateEntry) return false
        if (element.receiverExpression is KtSuperExpression) return false
        val callExpression = element.selectorExpression as? KtCallExpression ?: return false
        val referenceExpression = callExpression.calleeExpression as? KtNameReferenceExpression ?: return false
        return referenceExpression.getReferencedNameAsName() == OperatorNameConventions.TO_STRING && callExpression.valueArguments.isEmpty()
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtDotQualifiedExpression): Boolean {
        val call = element.resolveCall()?.successfulFunctionCallOrNull() ?: return false
        val allOverriddenSymbols = listOf(call.symbol) + call.symbol.getAllOverriddenSymbols()
        return allOverriddenSymbols.any { it.callableIdIfNonLocal == TO_STRING_CALLABLE_ID }
    }

    override fun apply(element: KtDotQualifiedExpression, project: Project, updater: ModPsiUpdater) {
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
