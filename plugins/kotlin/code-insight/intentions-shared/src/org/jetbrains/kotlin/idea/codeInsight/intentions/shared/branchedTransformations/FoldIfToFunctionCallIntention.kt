// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinModCommandWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AnalysisActionContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.Context
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.canFoldByPsi
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.fold
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.getFoldingContext
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class FoldIfToFunctionCallIntention : AbstractKotlinModCommandWithContext<KtIfExpression, Context>(KtIfExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("lift.function.call.out.of.if")

    override fun getActionName(element: KtIfExpression, context: Context): String = familyName

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtIfExpression> = applicabilityRange {
        it.ifKeyword.textRange.shiftLeft(it.startOffset)
    }

    override fun isApplicableByPsi(element: KtIfExpression): Boolean = canFoldByPsi(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtIfExpression): Context? = getFoldingContext(element)

    override fun apply(element: KtIfExpression, context: AnalysisActionContext<Context>, updater: ModPsiUpdater) {
        fold(element, context.analyzeContext)
    }
}
