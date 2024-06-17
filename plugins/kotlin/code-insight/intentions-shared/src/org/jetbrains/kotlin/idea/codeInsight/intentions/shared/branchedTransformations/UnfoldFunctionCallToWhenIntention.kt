// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.UnfoldFunctionCallToIfOrWhenUtils.canUnfold
import org.jetbrains.kotlin.idea.codeinsight.utils.UnfoldFunctionCallToIfOrWhenUtils.unfold
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class UnfoldFunctionCallToWhenIntention :
    KotlinApplicableModCommandAction<KtCallExpression, Unit>(KtCallExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("replace.function.call.with.when")

    override fun getApplicableRanges(element: KtCallExpression): List<TextRange> =
        ApplicabilityRanges.calleeExpression(element)

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = canUnfold<KtWhenExpression>(element)

    context(KaSession)
    override fun prepareContext(element: KtCallExpression) {
    }

    override fun invoke(
      actionContext: ActionContext,
      element: KtCallExpression,
      elementContext: Unit,
      updater: ModPsiUpdater,
    ) {
        unfold<KtWhenExpression>(element)
    }
}
