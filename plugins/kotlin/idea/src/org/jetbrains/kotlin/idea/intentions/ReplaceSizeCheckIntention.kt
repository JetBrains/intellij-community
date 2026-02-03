// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingOffsetIndependentIntention
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.util.function.Supplier

internal abstract class ReplaceSizeCheckIntention(textGetter: Supplier<@IntentionName String>) : SelfTargetingOffsetIndependentIntention<KtBinaryExpression>(
    KtBinaryExpression::class.java, textGetter
) {
    override fun applyTo(element: KtBinaryExpression, editor: Editor?) {
        val target = getTargetExpression(element) ?: return
        val replacement = getReplacement(target)
        element.replaced(replacement.newExpression())
    }

    override fun isApplicableTo(element: KtBinaryExpression): Boolean {
        val targetExpression = getTargetExpression(element) ?: return false

        val isSizeOrLength = targetExpression.isSizeOrLength()
        val isCountCall = targetExpression.isTargetCountCall()
        if (!isSizeOrLength && !isCountCall) return false

        val replacement = getReplacement(targetExpression, isCountCall)
        replacement.intentionTextGetter?.let { setTextGetter(it) }

        return true
    }

    protected abstract fun getTargetExpression(element: KtBinaryExpression): KtExpression?

    protected abstract fun getReplacement(expression: KtExpression, isCountCall: Boolean = expression.isTargetCountCall()): Replacement

    protected class Replacement(
        private val targetExpression: KtExpression,
        private val newFunctionCall: String,
        private val negate: Boolean = false,
        val intentionTextGetter: Supplier<@IntentionName String>? = null
    ) {
        fun newExpression(): KtExpression {
            val excl = if (negate) "!" else ""
            val receiver = if (targetExpression is KtDotQualifiedExpression) "${targetExpression.receiverExpression.text}." else ""
            return KtPsiFactory(targetExpression.project).createExpression("$excl$receiver$newFunctionCall")
        }
    }

    private fun KtExpression.isTargetCountCall() = isCountCall { it.valueArguments.isEmpty() }
}