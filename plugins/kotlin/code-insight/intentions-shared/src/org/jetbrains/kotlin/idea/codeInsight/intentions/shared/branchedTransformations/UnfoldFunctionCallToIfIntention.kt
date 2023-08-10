// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeInsight.intentions.shared.branchedTransformations

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.AbstractKotlinApplicableIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.utils.UnfoldFunctionCallToIfOrWhenUtils.canUnfold
import org.jetbrains.kotlin.idea.codeinsight.utils.UnfoldFunctionCallToIfOrWhenUtils.unfold
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.psiUtil.startOffset

internal class UnfoldFunctionCallToIfIntention : AbstractKotlinApplicableIntention<KtCallExpression>(KtCallExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("replace.function.call.with.if")

    override fun getActionName(element: KtCallExpression): String = familyName

    override fun getApplicabilityRange() = applicabilityRange<KtCallExpression> {
        it.calleeExpression?.textRange?.shiftLeft(it.startOffset)
    }

    override fun isApplicableByPsi(element: KtCallExpression): Boolean = canUnfold<KtIfExpression>(element)

    override fun apply(element: KtCallExpression, project: Project, editor: Editor?) {
        unfold<KtIfExpression>(element)
    }
}
