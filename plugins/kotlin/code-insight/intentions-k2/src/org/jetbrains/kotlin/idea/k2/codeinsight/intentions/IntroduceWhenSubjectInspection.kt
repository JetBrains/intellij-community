// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.AbstractKotlinApplicableInspectionWithContext
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.KotlinApplicabilityRange
import org.jetbrains.kotlin.idea.codeinsight.api.applicators.applicabilityTarget
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.getSubjectToIntroduce
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.introduceSubjectIfPossible
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class IntroduceWhenSubjectInspection :
    AbstractKotlinApplicableInspectionWithContext<KtWhenExpression, IntroduceWhenSubjectInspection.Context>() {

    data class Context(
        val subjectedExpression: KtWhenExpression,
        val commentSaver: CommentSaver,
        val subject: KtExpression,
    )

    override fun getProblemDescription(element: KtWhenExpression, context: Context): String =
        KotlinBundle.message("introduce.0.as.subject.0.when", context.subject.text)

    override fun createQuickFix(
        element: KtWhenExpression,
        context: Context,
    ) = object : KotlinModCommandQuickFix<KtWhenExpression>() {

        override fun applyFix(
            project: Project,
            element: KtWhenExpression,
            updater: ModPsiUpdater,
        ) {
            val result = element.replace(context.subjectedExpression)
            context.commentSaver.restore(result)
        }

        override fun getFamilyName(): String =
            KotlinBundle.message("introduce.when.subject")
    }

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ) = object : KtVisitorVoid() {

        override fun visitWhenExpression(expression: KtWhenExpression) {
            visitTargetElement(expression, holder, isOnTheFly)
        }
    }

    override fun getApplicabilityRange(): KotlinApplicabilityRange<KtWhenExpression> = applicabilityTarget { it.whenKeyword }

    context(KtAnalysisSession) override fun prepareContext(element: KtWhenExpression): Context? {
        val commentSaver = CommentSaver(element, true)
        val subject = element.getSubjectToIntroduce() ?: return null
        return Context(
            element.introduceSubjectIfPossible(subject),
            commentSaver,
            subject
        )
    }

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = true

    // Don't highlight it as a warning if only one branch is affected:
    // the resulting code with a 'when' subject will arguably be less clear than the original
    override fun getProblemHighlightType(element: KtWhenExpression, context: Context): ProblemHighlightType {
        val regularEntries = element.entries.filter { !it.isElse }
        return if (regularEntries.size < 2) ProblemHighlightType.INFORMATION else super.getProblemHighlightType(element, context)
    }
}
