// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.intentions.SelfTargetingRangeIntention
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.idea.refactoring.getLineNumber
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.KtValueArgumentList
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments

@Suppress("DEPRECATION")
class RemoveEmptyParenthesesFromLambdaCallInspection : IntentionBasedInspection<KtValueArgumentList>(
    RemoveEmptyParenthesesFromLambdaCallIntention::class
), CleanupLocalInspectionTool

class RemoveEmptyParenthesesFromLambdaCallIntention : SelfTargetingRangeIntention<KtValueArgumentList>(
    KtValueArgumentList::class.java, KotlinBundle.lazyMessage("remove.unnecessary.parentheses.from.function.call.with.lambda")
) {
    override fun applicabilityRange(element: KtValueArgumentList): TextRange? = Companion.applicabilityRange(element)

    override fun applyTo(element: KtValueArgumentList, editor: Editor?) = Companion.applyTo(element)

    companion object {
        fun isApplicable(list: KtValueArgumentList): Boolean = applicabilityRange(list) != null

        fun applyTo(list: KtValueArgumentList) = list.delete()

        fun applyToIfApplicable(list: KtValueArgumentList) {
            if (isApplicable(list)) {
                applyTo(list)
            }
        }

        private fun applicabilityRange(list: KtValueArgumentList): TextRange? {
            if (list.arguments.isNotEmpty()) return null
            val parent = list.parent as? KtCallExpression ?: return null
            if (parent.calleeExpression?.text == KtTokens.SUSPEND_KEYWORD.value) return null
            val singleLambdaArgument = parent.lambdaArguments.singleOrNull() ?: return null
            if (list.getLineNumber(start = false) != singleLambdaArgument.getLineNumber(start = true)) return null
            val prev = list.getPrevSiblingIgnoringWhitespaceAndComments()
            if (prev is KtCallExpression || (prev as? KtQualifiedExpression)?.callExpression != null) return null
            return list.textRange
        }
    }
}
