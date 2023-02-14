// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations.intentions

import com.intellij.codeInsight.intention.LowPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.safeAnalyzeNonSourceRootCode
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.idea.intentions.branchedTransformations.combineWhenConditions
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.isUsedAsExpression
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode

class WhenToIfIntention : SelfTargetingRangeIntention<KtWhenExpression>(
    KtWhenExpression::class.java,
    KotlinBundle.lazyMessage("replace.when.with.if")
), LowPriorityAction {
    override fun applicabilityRange(element: KtWhenExpression): TextRange? {
        val entries = element.entries
        if (entries.isEmpty()) return null
        val lastEntry = entries.last()
        if (entries.any { it != lastEntry && it.isElse }) return null
        if (entries.all { it.isElse }) return null // 'when' with only 'else' branch is not supported
        if (element.subjectExpression is KtProperty) return null
        if (!lastEntry.isElse) {
            val bindingContext = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL_WITH_CFA)
            if (bindingContext == BindingContext.EMPTY || element.isUsedAsExpression(bindingContext)) return null
        }
        return element.whenKeyword.textRange
    }

    override fun applyTo(element: KtWhenExpression, editor: Editor?) {
        val commentSaver = CommentSaver(element)
        val psiFactory = KtPsiFactory(element.project)

        val isTrueOrFalseCondition = element.isTrueOrFalseCondition()
        val ifExpression = psiFactory.buildExpression {
            val entries = element.entries
            for ((i, entry) in entries.withIndex()) {
                if (i > 0) {
                    appendFixedText("else ")
                }
                val branch = entry.expression
                if (entry.isElse || (isTrueOrFalseCondition && i == 1)) {
                    appendExpression(branch)
                } else {
                    val condition = psiFactory.combineWhenConditions(entry.conditions, element.subjectExpression)
                    appendFixedText("if (")
                    appendExpression(condition)
                    appendFixedText(")")
                    if (branch is KtIfExpression) {
                        appendFixedText("{ ")
                    }

                    appendExpression(branch)
                    if (branch is KtIfExpression) {
                        appendFixedText(" }")
                    }
                }

                if (i != entries.lastIndex) {
                    appendFixedText("\n")
                }
            }
        }

        val result = element.replace(ifExpression)
        commentSaver.restore(result)
    }

    private fun KtWhenExpression.isTrueOrFalseCondition(): Boolean {
        val entries = this.entries
        if (entries.size != 2) return false
        val first = entries[0]?.conditionExpression() ?: return false
        val second = entries[1]?.conditionExpression() ?: return false
        return first.isTrueConstant() && second.isFalseConstant() || first.isFalseConstant() && second.isTrueConstant()
    }

    private fun KtWhenEntry.conditionExpression(): KtExpression? {
        return (conditions.singleOrNull() as? KtWhenConditionWithExpression)?.expression
    }
}
