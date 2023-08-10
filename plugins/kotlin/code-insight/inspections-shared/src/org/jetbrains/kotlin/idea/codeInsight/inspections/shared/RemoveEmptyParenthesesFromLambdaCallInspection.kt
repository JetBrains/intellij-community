// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.analysis.api.calls.KtSuccessCallInfo
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspection
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.canRemoveByPsi
import org.jetbrains.kotlin.idea.codeinsights.impl.base.RemoveEmptyParenthesesFromLambdaCallUtils.removeArgumentList
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList

internal class RemoveEmptyParenthesesFromLambdaCallInspection : AbstractKotlinApplicableInspection<KtValueArgumentList>(KtValueArgumentList::class),
                                                                CleanupLocalInspectionTool {
    override fun getProblemDescription(element: KtValueArgumentList): String =
        KotlinBundle.message("inspection.remove.empty.parentheses.from.lambda.call.display.name")

    override fun getActionFamilyName(): String = KotlinBundle.message("inspection.remove.empty.parentheses.from.lambda.call.action.name")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgumentList> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtValueArgumentList): Boolean = canRemoveByPsi(element)

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtValueArgumentList): Boolean =
        (element.parent as? KtCallExpression)?.resolveCall() is KtSuccessCallInfo

    override fun apply(element: KtValueArgumentList, project: Project, editor: Editor?) {
        removeArgumentList(element)
    }
}