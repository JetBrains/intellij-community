// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.inspections.shared

import org.jetbrains.kotlin.idea.codeinsight.api.applicators.*
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.RemoveEmptyParenthesesFromLambdaCallApplicator
import org.jetbrains.kotlin.psi.KtValueArgumentList

class RemoveEmptyParenthesesFromLambdaCallInspection :
    AbstractKotlinApplicatorBasedInspection<KtValueArgumentList, KotlinApplicatorInput.Empty>(KtValueArgumentList::class) {
    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtValueArgumentList> = ApplicabilityRanges.SELF

    override fun getInputProvider(): KotlinApplicatorInputProvider<KtValueArgumentList, KotlinApplicatorInput.Empty> =
        inputProvider { KotlinApplicatorInput.Empty }

    override fun getApplicator(): KotlinApplicator<KtValueArgumentList, KotlinApplicatorInput.Empty> =
        RemoveEmptyParenthesesFromLambdaCallApplicator.applicator
}