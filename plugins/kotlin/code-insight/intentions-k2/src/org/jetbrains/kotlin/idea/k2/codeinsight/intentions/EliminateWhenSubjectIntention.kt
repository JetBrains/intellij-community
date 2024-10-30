// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations.generateNewConditionWithSubject
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtWhenExpression
import org.jetbrains.kotlin.psi.buildExpression

class EliminateWhenSubjectIntention : KotlinApplicableModCommandAction<KtWhenExpression, Boolean>(KtWhenExpression::class) {
    override fun getFamilyName(): @IntentionFamilyName String = KotlinBundle.message("inline.when.argument")
    override fun getPresentation(context: ActionContext, element: KtWhenExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    context(KaSession)
    override fun prepareContext(element: KtWhenExpression): Boolean? {
        val subjectExpression = element.subjectExpression
        if (subjectExpression !is KtNameReferenceExpression) return null
        if (element.entries.lastOrNull()?.isElse == true || !element.isUsedAsExpression) {
            return subjectExpression.expressionType?.isMarkedNullable == true
        }
        return null
    }

    override fun getApplicableRanges(element: KtWhenExpression): List<TextRange> {
        val endOffset = element.openBrace?.startOffsetInParent ?: return emptyList()
        return listOf(TextRange(0, endOffset))
    }

    override fun invoke(
        actionContext: ActionContext, element: KtWhenExpression, elementContext: Boolean, updater: ModPsiUpdater
    ) {
        val subject = element.subjectExpression ?: return

        val commentSaver = CommentSaver(element, saveLineBreaks = true)

        val whenExpression = KtPsiFactory(element.project).buildExpression {
            appendFixedText("when {\n")

            for (entry in element.entries) {
                val branchExpression = entry.expression

                if (entry.isElse) {
                    appendFixedText("else")
                } else {
                    appendExpressions(
                        entry.conditions.map { it.generateNewConditionWithSubject(subject, elementContext) }, separator = "||"
                    )
                }
                appendFixedText("->")

                appendExpression(branchExpression)
                appendFixedText("\n")
            }

            appendFixedText("}")
        }

        val result = element.replace(whenExpression)
        commentSaver.restore(result)
    }
}
