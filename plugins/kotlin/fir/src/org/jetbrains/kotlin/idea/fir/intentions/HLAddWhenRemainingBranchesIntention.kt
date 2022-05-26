// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.fir.intentions

import org.jetbrains.kotlin.diagnostics.WhenMissingCase
import org.jetbrains.kotlin.idea.fir.api.AbstractHLIntention
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicabilityRange
import org.jetbrains.kotlin.idea.fir.api.applicator.HLApplicatorInputProvider
import org.jetbrains.kotlin.idea.fir.api.applicator.inputProvider
import org.jetbrains.kotlin.idea.fir.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.quickfix.fixes.AddWhenRemainingBranchFixFactories
import org.jetbrains.kotlin.psi.KtWhenExpression

class HLAddWhenRemainingBranchesIntention : AbstractHLIntention<KtWhenExpression, AddWhenRemainingBranchFixFactories.Input>(
    KtWhenExpression::class,
    AddWhenRemainingBranchFixFactories.applicator
) {
    override val applicabilityRange: HLApplicabilityRange<KtWhenExpression> get() = ApplicabilityRanges.SELF

    override val inputProvider: HLApplicatorInputProvider<KtWhenExpression, AddWhenRemainingBranchFixFactories.Input>
        get() = inputProvider { element ->
            val whenMissingCases = element.getMissingCases().takeIf {
                it.isNotEmpty() && it.singleOrNull() != WhenMissingCase.Unknown
            } ?: return@inputProvider null
            AddWhenRemainingBranchFixFactories.Input(whenMissingCases, null)
        }
}