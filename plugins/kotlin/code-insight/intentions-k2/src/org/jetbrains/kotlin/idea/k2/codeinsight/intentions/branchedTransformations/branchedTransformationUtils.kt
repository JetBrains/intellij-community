// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KtAnalysisSession
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
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
fun KtWhenCondition.generateNewConditionWithSubject(subject: KtExpression?, isNullableSubject: Boolean): KtExpression {
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
fun KtPsiFactory.combineWhenConditions(conditions: Array<KtWhenCondition>, subject: KtExpression?, isNullableSubject: Boolean) =
    when (conditions.size) {
        0 -> null
        1 -> conditions[0].generateNewConditionWithSubject(subject, isNullableSubject)
        else -> buildExpression {
            appendExpressions(conditions.map { it.generateNewConditionWithSubject(subject, isNullableSubject) }, separator = "||")
        }
    }

/**
 * Returns a new [KtWhenExpression] with introduced subject in brackets.
 * Returns old when expression if it was not possible to introduce a subject.
 * For example,
 *
 * ```
 * // Before:
 *  when {
 *    x is String -> continue
 *    x is Int -> break
 *    else -> println(x)
 *  }
 *
 * // After:
 *  when (x) {
 *    is String -> continue
 *    is Int -> break
 *    else -> println(x)
 *  }
 * ```
 */
context(KtAnalysisSession)
fun KtWhenExpression.introduceSubjectIfPossible(subject: KtExpression?, context: PsiElement = this): KtWhenExpression {
    subject ?: return this

    val psiFactory = KtPsiFactory.contextual(context)

    return psiFactory.buildExpression {
        appendFixedText("when(").appendExpression(subject).appendFixedText("){\n")

        for (entry in entries) {
            val branchExpression = entry.expression

            if (entry.isElse) {
                appendFixedText("else")
            } else {
                for ((i, condition) in entry.conditions.withIndex()) {
                    if (i > 0) appendFixedText(",")

                    val conditionExpression = (condition as KtWhenConditionWithExpression).expression
                    if (conditionExpression != null) {
                        val codeFragment = psiFactory.createExpressionCodeFragment(conditionExpression.text, context).getContentElement() as KtExpression
                        appendConditionWithSubjectRemoved(codeFragment, subject)
                    }
                }
            }
            appendFixedText("->")

            appendExpression(branchExpression)
            appendFixedText("\n")
        }

        appendFixedText("}")
    } as KtWhenExpression
}

/**
 * Returns [KtExpression] as potential subject for [KtWhenExpression].
 */
context(KtAnalysisSession)
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

context(KtAnalysisSession)
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

                else -> error("Unexpected operation token=${conditionExpression.operationToken}")
            }
        }

        else -> error("${conditionExpression?.let { it::class }} - is unsupported type of conditional expression")
    }
}


context(KtAnalysisSession)
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
                    val leftCandidate = lhs?.safeDeparenthesize().getCandidate()
                    val rightCandidate = rhs?.safeDeparenthesize().getCandidate()
                    if (leftCandidate.matches(rightCandidate)) leftCandidate else null
                }

                else -> null
            }
        }

        else -> null
    }
    return getCandidate()?.takeIf {
        it is KtNameReferenceExpression || (it as? KtQualifiedExpression)?.selectorExpression is KtNameReferenceExpression || it is KtThisExpression
    }
}

fun KtExpression.hasCandidateNameReferenceExpression(checkConstants: Boolean): Boolean {
    val nameReferenceExpression =
        this as? KtNameReferenceExpression ?: (this as? KtQualifiedExpression)?.selectorExpression as? KtNameReferenceExpression
        ?: return false
    if (!checkConstants) {
        return true
    }
    val resolved = nameReferenceExpression.mainReference.resolve()
    return !(resolved is KtObjectDeclaration || (resolved as? KtProperty)?.hasModifier(KtTokens.CONST_KEYWORD) == true)
}

context(KtAnalysisSession)
fun KtExpression?.matches(right: KtExpression?): Boolean {
    if (this == null && right == null) return true

    if (this != null && right != null) {
        return this.isSemanticMatch(right)
    }

    return false
}