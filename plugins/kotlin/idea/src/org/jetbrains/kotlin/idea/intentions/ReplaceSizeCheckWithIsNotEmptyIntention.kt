// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.intentions

import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.IntentionBasedInspection
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

@Suppress("DEPRECATION")
class ReplaceSizeCheckWithIsNotEmptyInspection : IntentionBasedInspection<KtBinaryExpression>(
    ReplaceSizeCheckWithIsNotEmptyIntention::class
) {
    override fun inspectionProblemText(element: KtBinaryExpression): String {
        return KotlinBundle.message("inspection.replace.size.check.with.is.not.empty.display.name")
    }
}

internal class ReplaceSizeCheckWithIsNotEmptyIntention : ReplaceSizeCheckIntention(KotlinBundle.messagePointer("replace.size.check.with.isnotempty")) {
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

    override fun getReplacement(expression: KtExpression, isCountCall: Boolean): Replacement {
        return if (isCountCall && expression.isRange() /* Ranges don't have isNotEmpty function: KT-51560 */) {
            Replacement(
                targetExpression = expression,
                newFunctionCall = "isEmpty()",
                negate = true,
                intentionTextGetter = KotlinBundle.messagePointer("replace.size.check.with.0", "!isEmpty")
            )
        } else {
            Replacement(
                targetExpression = expression,
                newFunctionCall = "isNotEmpty()",
                negate = false,
                intentionTextGetter = KotlinBundle.messagePointer("replace.size.check.with.0", "isNotEmpty")
            )
        }
    }

    private fun KtExpression.isRange(): Boolean {
        val receiver = resolveToCall()?.let { it.extensionReceiver ?: it.dispatchReceiver } ?: return false
        return receiver.type.constructor.declarationDescriptor.safeAs<ClassDescriptor>()?.isRange() == true
    }
}