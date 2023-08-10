// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntentionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.Context
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.getFoldingContext
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.canFoldByPsi
import org.jetbrains.kotlin.idea.codeinsight.utils.FoldIfOrWhenToFunctionCallUtils.fold
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class FoldWhenToFunctionCallIntention : AbstractKotlinApplicableIntentionWithContext<KtWhenExpression, Context>(KtWhenExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("lift.function.call.out.of.when")

    override fun getActionName(element: KtWhenExpression, context: Context): String = familyName

    override fun getApplicabilityRange() = applicabilityRange<KtWhenExpression> {
        it.whenKeyword.textRange.shiftLeft(it.startOffset)
    }

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = canFoldByPsi(element)

    context(KtAnalysisSession)
    override fun prepareContext(element: KtWhenExpression): Context? = getFoldingContext(element)

    override fun apply(element: KtWhenExpression, context: Context, project: Project, editor: Editor?) {
        fold(element, context)
    }
}