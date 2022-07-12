// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.AddRemainingWhenBranchesApplicator
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtWhenExpression

class AddWhenRemainingBranchesIntention
    : AbstractKotlinApplicatorBasedIntention<KtWhenExpression, AddRemainingWhenBranchesApplicator.Input>(KtWhenExpression::class,) {
    override val applicator get() =
        AddRemainingWhenBranchesApplicator.applicator

    override val applicabilityRange: KotlinApplicabilityRange<KtWhenExpression> get() = ApplicabilityRanges.SELF

    override val inputProvider: KotlinApplicatorInputProvider<KtWhenExpression, AddRemainingWhenBranchesApplicator.Input>
        get() = inputProvider { element ->
            val whenMissingCases = element.getMissingCases().takeIf {
                it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
            } ?: return@inputProvider null
            AddRemainingWhenBranchesApplicator.Input(whenMissingCases, enumToStarImport = null)
        }
}