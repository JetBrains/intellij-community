// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinApplicableInspectionBase
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsights.impl.base.applicators.ApplicabilityRanges
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.getSubjectToIntroduce
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.introduceSubjectIfPossible
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.KtWhenExpression

internal class IntroduceWhenSubjectInspection :
  KotlinApplicableInspectionBase.Simple<KtWhenExpression, String>() {

    override fun getProblemDescription(
      element: KtWhenExpression,
      context: String,
    ): String = KotlinBundle.message("introduce.0.as.subject.0.when", context)

    override fun createQuickFix(
      element: KtWhenExpression,
      context: String,
    ): KotlinModCommandQuickFix<KtWhenExpression> = object : KotlinModCommandQuickFix<KtWhenExpression>() {

        override fun applyFix(
          project: Project,
          element: KtWhenExpression,
          updater: ModPsiUpdater,
        ) {
            val commentSaver = CommentSaver(element, true)

            // If we use the subject from 'prepareContext' phase we can't restore comments on the result element
            val subjectedExpression = analyze(element) {
              val subject = element.getSubjectToIntroduce() ?: return
              element.introduceSubjectIfPossible(subject)
            }

            val result = element.replace(subjectedExpression)
            commentSaver.restore(result)
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

    override fun getApplicableRanges(element: KtWhenExpression): List<TextRange> =
        ApplicabilityRanges.whenKeyword(element)

    override fun KaSession.prepareContext(element: KtWhenExpression): String? =
        element.getSubjectToIntroduce()?.text

    override fun isApplicableByPsi(element: KtWhenExpression): Boolean = true

    // Don't highlight it as a warning if only one branch is affected:
    // the resulting code with a 'when' subject will arguably be less clear than the original
    override fun getProblemHighlightType(element: KtWhenExpression, context: String): ProblemHighlightType {
        val regularEntries = element.entries.filter { !it.isElse }
        return if (regularEntries.size < 2) ProblemHighlightType.INFORMATION else super.getProblemHighlightType(element, context)
    }
}