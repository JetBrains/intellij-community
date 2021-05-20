// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions

import org.jetbrains.kotlin.idea.KotlinBundle
import org.jetbrains.kotlin.idea.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression

@Suppress("DEPRECATION")
class ReplaceSizeCheckWithIsNotEmptyInspection : IntentionBasedInspection<KtBinaryExpression>(
    ReplaceSizeCheckWithIsNotEmptyIntention::class
) {
    override fun inspectionProblemText(element: KtBinaryExpression): String {
        return KotlinBundle.message("inspection.replace.size.check.with.is.not.empty.display.name")
    }
}

class ReplaceSizeCheckWithIsNotEmptyIntention : ReplaceSizeCheckIntention(KotlinBundle.lazyMessage("replace.size.check.with.isnotempty")) {
    override fun getGenerateMethodSymbol() = "isNotEmpty()"

    override fun getTargetExpression(element: KtBinaryExpression): KtExpression? = when (element.operationToken) {
        KtTokens.EXCLEQ -> when {
            element.right.isZero() -> element.left
            element.left.isZero() -> element.right
            else -> null
        }
        KtTokens.GT -> if (element.right.isZero()) element.left else null
        KtTokens.LT -> if (element.left.isZero()) element.right else null
        KtTokens.GTEQ -> if (element.right.isOne()) element.left else null
        KtTokens.LTEQ -> if (element.left.isOne()) element.right else null
        else -> null
    }
}