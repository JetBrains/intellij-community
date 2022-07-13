// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.AbstractKotlinApplicatorBasedIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.inputProvider
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddRemainingWhenBranchesApplicator
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class AddWhenRemainingBranchesIntention
    : AbstractKotlinApplicatorBasedIntention<KtWhenExpression, AddRemainingWhenBranchesApplicator.Input>(KtWhenExpression::class) {
    override fun getApplicator() =
        AddRemainingWhenBranchesApplicator.applicator

    override fun getApplicabilityRange() = ApplicabilityRanges.SELF

    override fun getInputProvider() = inputProvider { whenExpression: KtWhenExpression ->
        val whenMissingCases = whenExpression.getMissingCases().takeIf {
            it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
        } ?: return@inputProvider null
        AddRemainingWhenBranchesApplicator.Input(whenMissingCases, enumToStarImport = null)
    }
}