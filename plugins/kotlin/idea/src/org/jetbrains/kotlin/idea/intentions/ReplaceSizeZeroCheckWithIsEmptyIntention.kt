// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

@Suppress("DEPRECATION")
class ReplaceSizeZeroCheckWithIsEmptyInspection : IntentionBasedInspection<KtBinaryExpression>(
    ReplaceSizeZeroCheckWithIsEmptyIntention::class
) {
    override fun inspectionProblemText(element: KtBinaryExpression): String {
        return KotlinBundle.message("inspection.replace.size.zero.check.with.is.empty.display.name")
    }
}

internal class ReplaceSizeZeroCheckWithIsEmptyIntention : ReplaceSizeCheckIntention(
    KotlinBundle.messagePointer("replace.size.zero.check.with.isempty")
) {
    override fun getTargetExpression(element: KtBinaryExpression): KtExpression? = when (element.operationToken) {
        KtTokens.EQEQ -> when {
            element.right.isZero() -> element.left
            element.left.isZero() -> element.right
            else -> null
        }
        KtTokens.GT -> if (element.left.isOne()) element.right else null
        KtTokens.LT -> if (element.right.isOne()) element.left else null
        KtTokens.GTEQ -> if (element.left.isZero()) element.right else null
        KtTokens.LTEQ -> if (element.right.isZero()) element.left else null
        else -> null
    }

    override fun getReplacement(expression: KtExpression, isCountCall: Boolean) = Replacement(expression, "isEmpty()")
}