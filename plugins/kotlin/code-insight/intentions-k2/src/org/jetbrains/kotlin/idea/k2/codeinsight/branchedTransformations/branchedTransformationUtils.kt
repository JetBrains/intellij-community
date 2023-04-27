// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.branchedTransformations

import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.psi.*

/**
 * A function to generate a new [KtExpression] for the new condition with [subject].
 *
 * The new condition will be in the form of "existing-condition op [subject]". For example,
 *
 * // Before:
 *  when (n) {
 *    is Int -> ..
 *  }
 *
 * // After:
 *  n is Int ..
 */
private fun KtWhenCondition.generateNewConditionWithSubject(subject: KtExpression?, isNullableSubject: Boolean): KtExpression {
    val psiFactory = KtPsiFactory(project)

    return when (this) {
        is KtWhenConditionIsPattern -> {
            val op = if (isNegated) "!is" else "is"
            psiFactory.createExpressionByPattern("$0 $op $1", subject ?: "_", typeReference ?: "")
        }

        is KtWhenConditionInRange -> {
            val op = operationReference.text
            psiFactory.createExpressionByPattern("$0 $op $1", subject ?: "_", rangeExpression ?: "")
        }

        is KtWhenConditionWithExpression -> {
            if (subject != null) {
                when {
                    expression?.isTrueConstant() == true && !isNullableSubject -> subject
                    expression?.isFalseConstant() == true && !isNullableSubject -> subject.negate()
                    else -> psiFactory.createExpressionByPattern("$0 == $1", subject, expression ?: "")
                }
            } else {
                expression ?: throw IllegalArgumentException("Unexpected null expression of KtWhenCondition: $this")
            }
        }

        else -> throw IllegalArgumentException("Unknown KtWhenCondition type: $this")
    }
}

/**
 * Returns a new condition expression by combining conditions of a when expression with '||' operation. For example,
 *
 * // Before:
 *  when (n) {
 *    0, 3 -> ..
 *  }
 *
 * // After:
 *  n == 0 || n == 3 ..
 */
internal fun KtPsiFactory.combineWhenConditions(conditions: Array<KtWhenCondition>, subject: KtExpression?, isNullableSubject: Boolean) =
    when (conditions.size) {
        0 -> null
        1 -> conditions[0].generateNewConditionWithSubject(subject, isNullableSubject)
        else -> buildExpression {
            appendExpressions(conditions.map { it.generateNewConditionWithSubject(subject, isNullableSubject) }, separator = "||")
        }
    }