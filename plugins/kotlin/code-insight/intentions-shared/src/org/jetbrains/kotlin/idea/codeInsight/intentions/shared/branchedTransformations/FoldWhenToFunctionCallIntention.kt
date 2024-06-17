// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.Context
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.canFoldByPsi
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.fold
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.getFoldingContext
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class FoldWhenToFunctionCallIntention :
    KotlinApplicableModCommandAction<KtWhenExpression, Context>(KtWhenExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("lift.function.call.out.of.when")

    override fun getApplicableRanges(element: KtWhenExpression): List<TextRange> =
        ApplicabilityRanges.whenKeyword(element)

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = canFoldByPsi(element)

    context(KaSession)
    override fun prepareContext(element: KtWhenExpression): Context? = getFoldingContext(element)

    override fun invoke(
      actionContext: ActionContext,
      element: KtWhenExpression,
      elementContext: Context,
      updater: ModPsiUpdater,
    ) {
        fold(element, elementContext)
    }
}