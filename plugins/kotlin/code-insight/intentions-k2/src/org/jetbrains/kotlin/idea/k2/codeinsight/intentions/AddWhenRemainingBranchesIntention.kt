// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils
import org.jetbrains.kotlin.idea.codeinsights.impl.base.intentions.AddRemainingWhenBranchesUtils.addRemainingWhenBranches
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class AddWhenRemainingBranchesIntention
    : AbstractKotlinModCommandWithContext<KtWhenExpression, AddRemainingWhenBranchesUtils.Context>(KtWhenExpression::class) {

    override fun getFamilyName(): String = AddRemainingWhenBranchesUtils.familyAndActionName(false)
    override fun getActionName(element: KtWhenExpression, context: AddRemainingWhenBranchesUtils.Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtWhenExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = true

    context(KtAnalysisSession)
    override fun prepareContext(element: KtWhenExpression): AddRemainingWhenBranchesUtils.Context? {
        val whenMissingCases = element.getMissingCases().takeIf {
            it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
        } ?: return null
        return AddRemainingWhenBranchesUtils.Context(whenMissingCases, enumToStarImport = null)
    }

    override fun apply(
        element: KtWhenExpression,
        context: AnalysisActionContext<AddRemainingWhenBranchesUtils.Context>,
        updater: ModPsiUpdater
    ) {
        addRemainingWhenBranches(element, context.analyzeContext)
    }
}