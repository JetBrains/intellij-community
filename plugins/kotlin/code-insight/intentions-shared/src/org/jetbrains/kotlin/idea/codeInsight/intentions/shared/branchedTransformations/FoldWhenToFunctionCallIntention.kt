// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.Context
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.canFoldByPsi
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.fold
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.getFoldingContext
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class FoldWhenToFunctionCallIntention :
    KotlinApplicableModCommandAction<KtWhenExpression, Context>(KtWhenExpression::class) {

    override fun getFamilyName(): String = KotlinBundle.message("lift.function.call.out.of.when")

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtWhenExpression> = applicabilityRange {
        it.whenKeyword.textRange.shiftLeft(it.startOffset)
    }

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = canFoldByPsi(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtWhenExpression): Context? = getFoldingContext(element)

    override fun invoke(
        context: ActionContext,
        element: KtWhenExpression,
        elementContext: Context,
        updater: ModPsiUpdater,
    ) {
        fold(element, elementContext)
    }
}