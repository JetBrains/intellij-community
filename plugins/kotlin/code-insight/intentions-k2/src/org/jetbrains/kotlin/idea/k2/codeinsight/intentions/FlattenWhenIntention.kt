// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinPsiUpdateModCommandIntention
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.matches
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtWhenExpression

class FlattenWhenIntention : KotlinPsiUpdateModCommandIntention<KtWhenExpression>(KtWhenExpression::class) {
    override fun getFamilyName(): String = KotlinBundle.message("flatten.when.expression")

    override fun invoke(context: ActionContext, element: KtWhenExpression, updater: ModPsiUpdater) {
        val commentSaver = CommentSaver(element)
        val nestedWhen = element.elseExpression as KtWhenExpression

        if (nestedWhen.entries.isNotEmpty()) {
            element.addRangeAfter(nestedWhen.entries.first(), nestedWhen.entries.last(), element.entries.last())
        }

        nestedWhen.parent.delete()
        commentSaver.restore(element)

        updater.moveCaretTo(element)
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtWhenExpression> = ApplicabilityRanges.SELF

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean {
        val subject = element.subjectExpression
        if (subject != null && subject !is KtNameReferenceExpression) return false

        return KtPsiUtil.checkWhenExpressionHasSingleElse(element)
    }

    context(KtAnalysisSession)
    override fun isApplicableByAnalyze(element: KtWhenExpression): Boolean {
        val subject = element.subjectExpression

        val elseEntry = element.entries.singleOrNull { it.isElse } ?: return false
        val innerWhen = elseEntry.expression as? KtWhenExpression ?: return false

        return subject.matches(innerWhen.subjectExpression)
    }
}
