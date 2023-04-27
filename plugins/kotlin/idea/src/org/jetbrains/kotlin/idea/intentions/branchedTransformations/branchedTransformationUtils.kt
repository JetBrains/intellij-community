// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.intentions.branchedTransformations

import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.idea.intentions.negate
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.idea.util.CommentSaver
import org.jetbrains.kotlin.idea.util.psi.patternMatching.matches
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.util.getType
import org.jetbrains.kotlin.resolve.lazy.BodyResolveMode
import org.jetbrains.kotlin.types.isNullable

fun KtWhenCondition.toExpression(subject: KtExpression?, subjectContext: BindingContext? = null): KtExpression {
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
                val subjectType = subject.getType(subjectContext ?: subject.analyze(BodyResolveMode.PARTIAL))
                val isNullableSubject = subjectType?.isNullable() == true
                when {
                    expression?.isTrueConstant() == true && !isNullableSubject -> subject
                    expression?.isFalseConstant() == true && !isNullableSubject -> subject.negate()
                    else -> psiFactory.createExpressionByPattern("$0 == $1", subject, expression ?: "")
                }
            } else {
                expression!!
            }
        }

        else -> throw IllegalArgumentException("Unknown JetWhenCondition type: $this")
    }
}

fun KtWhenExpression.getSubjectToIntroduce(checkConstants: Boolean = true): KtExpression? {
    if (subjectExpression != null) return null

    var lastCandidate: KtExpression? = null
    for (entry in entries) {
        val conditions = entry.conditions
        if (!entry.isElse && conditions.isEmpty()) return null

        for (condition in conditions) {
            if (condition !is KtWhenConditionWithExpression) return null
            val candidate = condition.expression?.getWhenConditionSubjectCandidate(checkConstants) ?: return null
            if (lastCandidate == null) {
                lastCandidate = candidate
            } else if (!lastCandidate.matches(candidate)) {
                return null
            }
        }
    }

    return lastCandidate
}

fun KtExpression?.getWhenConditionSubjectCandidate(checkConstants: Boolean): KtExpression? {
    fun KtExpression?.getCandidate(): KtExpression? = when (this) {
        is KtIsExpression -> leftHandSide
        is KtBinaryExpression -> {
            val lhs = left
            val rhs = right
            when (operationToken) {
                KtTokens.IN_KEYWORD, KtTokens.NOT_IN -> lhs
                KtTokens.EQEQ ->
                    lhs?.takeIf { it.hasCandidateNameReferenceExpression(checkConstants) }
                        ?: rhs?.takeIf { it.hasCandidateNameReferenceExpression(checkConstants) }
                KtTokens.OROR -> {
                    val leftCandidate = lhs.getCandidate()
                    val rightCandidate = rhs.getCandidate()
                    if (leftCandidate.matches(rightCandidate)) leftCandidate else null
                }
                else -> null
            }
        }
        else -> null
    }
    return getCandidate()?.takeIf {
        it is KtNameReferenceExpression
                || (it as? KtQualifiedExpression)?.selectorExpression is KtNameReferenceExpression
                || it is KtThisExpression
    }
}

private fun KtExpression.hasCandidateNameReferenceExpression(checkConstants: Boolean): Boolean {
    val nameReferenceExpression = this as? KtNameReferenceExpression
        ?: (this as? KtQualifiedExpression)?.selectorExpression as? KtNameReferenceExpression
        ?: return false
    if (!checkConstants) {
        return true
    }
    val resolved = nameReferenceExpression.mainReference.resolve()
    if (resolved is KtObjectDeclaration || (resolved as? KtProperty)?.hasModifier(KtTokens.CONST_KEYWORD) == true) return false
    return true
}

fun KtWhenExpression.introduceSubject(checkConstants: Boolean = true): KtWhenExpression? {
    val subject = getSubjectToIntroduce(checkConstants) ?: return null

    val commentSaver = CommentSaver(this, saveLineBreaks = true)

    val whenExpression = KtPsiFactory(project).buildExpression {
        appendFixedText("when(").appendExpression(subject).appendFixedText("){\n")

        for (entry in entries) {
            val branchExpression = entry.expression

            if (entry.isElse) {
                appendFixedText("else")
            } else {
                for ((i, condition) in entry.conditions.withIndex()) {
                    if (i > 0) appendFixedText(",")

                    val conditionExpression = (condition as KtWhenConditionWithExpression).expression
                    appendConditionWithSubjectRemoved(conditionExpression, subject)
                }
            }
            appendFixedText("->")

            appendExpression(branchExpression)
            appendFixedText("\n")
        }

        appendFixedText("}")
    } as KtWhenExpression

    val result = replaced(whenExpression)
    commentSaver.restore(result)
    return result
}

private fun BuilderByPattern<KtExpression>.appendConditionWithSubjectRemoved(conditionExpression: KtExpression?, subject: KtExpression) {
    when (conditionExpression) {
        is KtIsExpression -> {
            if (conditionExpression.isNegated) {
                appendFixedText("!")
            }
            appendFixedText("is ")
            appendNonFormattedText(conditionExpression.typeReference?.text ?: "")
        }

        is KtBinaryExpression -> {
            val lhs = conditionExpression.left
            val rhs = conditionExpression.right
            when (conditionExpression.operationToken) {
                KtTokens.IN_KEYWORD -> appendFixedText("in ").appendExpression(rhs)
                KtTokens.NOT_IN -> appendFixedText("!in ").appendExpression(rhs)
                KtTokens.EQEQ -> appendExpression(if (subject.matches(lhs)) rhs else lhs)
                KtTokens.OROR -> {
                    appendConditionWithSubjectRemoved(lhs, subject)
                    appendFixedText(", ")
                    appendConditionWithSubjectRemoved(rhs, subject)
                }
                else -> throw IllegalStateException()
            }
        }

        else -> throw IllegalStateException()
    }
}

fun KtPsiFactory.combineWhenConditions(conditions: Array<KtWhenCondition>, subject: KtExpression?) = when (conditions.size) {
    0 -> null
    1 -> conditions[0].toExpression(subject)
    else -> buildExpression {
        val subjectContext = subject?.analyze(BodyResolveMode.PARTIAL)
        appendExpressions(conditions.map { it.toExpression(subject, subjectContext) }, separator = "||")
    }
}