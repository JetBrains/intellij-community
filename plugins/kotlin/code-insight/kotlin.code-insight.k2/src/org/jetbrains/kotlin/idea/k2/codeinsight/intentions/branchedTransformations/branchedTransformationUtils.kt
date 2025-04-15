// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.branchedTransformations

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.idea.base.projectStructure.languageVersionSettings
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.psi.safeDeparenthesize
import org.jetbrains.kotlin.idea.codeinsight.utils.isFalseConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.isTrueConstant
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.K2SemanticMatcher.isSemanticMatch
import org.jetbrains.kotlin.idea.k2.refactoring.introduce.introduceVariable.K2IntroduceVariableHandler
import org.jetbrains.kotlin.idea.references.mainReference
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.utils.KotlinExceptionWithAttachments

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
 *
 *  If a guard condition is present, it will be combined with the last condition with the '&&' operation.
 *  This will be done regardless of the number of entry conditions.
 *  Even though it's not correct to use `when` guards with multiple conditions in one branch, transforming it in user code is sensible.
 *  It preserves the intended meaning and makes the code correct.
 *
 *  For example:
 *  ```
 *  when (foo) {
 *      is String,
 *      is Int if foo > 0 -> ..
 *  }
 *  ```
 *  transforms nicely into
 *  ```
 *  if (foo is String || foo is Int && foo > 0) ..
 *  ```
 */
fun KtPsiFactory.combineWhenConditions(entry: KtWhenEntry, subject: KtExpression?, isNullableSubject: Boolean): KtExpression? {
    val conditions = entry.conditions
    val combinedConditionsWithSubject = when (conditions.size) {
        0 -> null
        1 -> conditions[0].generateNewConditionWithSubject(subject, isNullableSubject)
        else -> buildExpression {
            appendExpressions(conditions.map { it.generateNewConditionWithSubject(subject, isNullableSubject) }, separator = "||")
        }
    }
    return handleWhenGuard(combinedConditionsWithSubject, entry)
}

private fun KtPsiFactory.handleWhenGuard(ktExpression: KtExpression?, entry: KtWhenEntry): KtExpression? {
    val guardExpression = entry.guard?.getExpression()
    if (ktExpression == null) return guardExpression
    if (guardExpression == null) return ktExpression

    val needParentheses = guardExpression is KtBinaryExpression && guardExpression.operationToken == KtTokens.OROR
    val preparedGuard = if (needParentheses) createExpression("(${guardExpression.text})") else guardExpression
    return createExpression("${ktExpression.text} && ${preparedGuard.text}")
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
context(KaSession)
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
                        val codeFragment =
                            psiFactory.createExpressionCodeFragment(conditionExpression.text, context).getContentElement() as KtExpression
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
context(KaSession)
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

context(KaSession)
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

                KtTokens.ANDAND -> {
                    appendConditionWithSubjectRemoved(lhs, subject)
                    if (lhs is KtBinaryExpression && lhs.operationToken == KtTokens.ANDAND) {
                        appendFixedText(" && ")
                    } else {
                        appendFixedText(" if ")
                    }
                    appendExpression(rhs)
                }

                else -> error("Unexpected operation token=${conditionExpression.operationToken}")
            }
        }

        else -> error("${conditionExpression?.let { it::class }} - is unsupported type of conditional expression")
    }
}

/**
 * Search state for the `when` subject introduction.
 *
 * This state helps to track which boolean operators are allowed for a given candidate branch transformation.
 * The `&&` and `||` operators are mutually exclusive for the subject introduction.
 * Once one of them is encountered, meeting the other one blocks the transformation.
 */
private enum class SearchState {
    /* both `||` and `&&` are allowed */
    UNCONSTRAINED,
    /* only `&&` is allowed */
    AND_ONLY,
    /* only `||` is allowed */
    OR_ONLY,
    /* met both `&&` and `||`, the transformation is not possible */
    INCOMPATIBLE,
    /* when guards are not enabled, only `||` are allowed */
    GUARDS_NOT_SUPPORTED;

    operator fun plus(other: SearchState): SearchState = when {
        this == GUARDS_NOT_SUPPORTED || other == GUARDS_NOT_SUPPORTED -> GUARDS_NOT_SUPPORTED
        this == INCOMPATIBLE || other == INCOMPATIBLE -> INCOMPATIBLE
        this == UNCONSTRAINED -> other
        other == UNCONSTRAINED -> this
        this == other -> this
        else -> INCOMPATIBLE
    }
}

context(KaSession)
fun KtExpression?.getWhenConditionSubjectCandidate(checkConstants: Boolean): KtExpression? {
    if (this == null) return null
    fun KtExpression?.getCandidate(state: SearchState): KtExpression? {
        if (state == SearchState.INCOMPATIBLE) return null
        return when (this) {
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
                        val nextStepState = state + SearchState.OR_ONLY
                        val leftCandidate = lhs?.safeDeparenthesize().getCandidate(nextStepState)
                        val rightCandidate = rhs?.safeDeparenthesize().getCandidate(nextStepState)
                        if (leftCandidate.matches(rightCandidate)) leftCandidate else null
                    }

                    KtTokens.ANDAND -> {
                        if (state == SearchState.GUARDS_NOT_SUPPORTED) null
                        else deepestLhsOfAndAndChain()?.getCandidate(state + SearchState.AND_ONLY)
                    }

                    else -> null
                }
            }

            else -> null
        }
    }

    val initialSearchState: SearchState =
        if (languageVersionSettings.supportsFeature(LanguageFeature.WhenGuards)) SearchState.UNCONSTRAINED
        else SearchState.GUARDS_NOT_SUPPORTED
    return getCandidate(initialSearchState)?.takeIf {
        it is KtNameReferenceExpression
                || (it as? KtQualifiedExpression)?.selectorExpression is KtNameReferenceExpression
                || it is KtThisExpression
    }
}

private fun KtBinaryExpression.deepestLhsOfAndAndChain(): KtExpression? {
    val lhs = left?.safeDeparenthesize() ?: return null
    return if (lhs is KtBinaryExpression && lhs.operationToken == KtTokens.ANDAND) {
        lhs.deepestLhsOfAndAndChain()
    } else {
        lhs
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

context(KaSession)
fun KtExpression?.matches(right: KtExpression?): Boolean {
    if (this == null && right == null) return true

    if (this != null && right != null) {
        return this.isSemanticMatch(right)
    }

    return false
}

fun KtIfExpression.introduceValueForCondition(occurrenceInThenClause: KtExpression, editor: Editor?) {
    val occurrenceInConditional = when (val condition = condition) {
        is KtBinaryExpression -> condition.left
        is KtIsExpression -> condition.leftHandSide
        else -> throw KotlinExceptionWithAttachments("Only binary / is expressions are supported here: ${condition?.let { it::class.java }}")
            .withPsiAttachment("condition", condition)
    }!!
    K2IntroduceVariableHandler.collectCandidateTargetContainersAndDoRefactoring(
        project = project,
        editor = editor,
        expressionToExtract = occurrenceInConditional,
        isVar = false,
        occurrencesToReplace = listOf(occurrenceInConditional, occurrenceInThenClause),
        onNonInteractiveFinish = null,
    )
}

fun KtExpression.isPure(): Boolean {
    val expr = safeDeparenthesize()
    if (expr is KtSimpleNameExpression) {
        val target = expr.mainReference.resolve()
        return when {
            target is KtProperty && (target.isLocal || target.initializer != null && !target.isVar) -> {
                true
            }

            target is KtParameter && !(target.isPropertyParameter() && target.isMutable) -> {
                true
            }

            else -> false
        }
    } else if (expr is KtQualifiedExpression) {
        return expr.receiverExpression.isPure() && expr.selectorExpression?.isPure() != false
    }
    return false
}

fun KtExpression.convertToIfNotNullExpression(
    conditionLhs: KtExpression,
    thenClause: KtExpression,
    elseClause: KtExpression?
): KtIfExpression {
    val condition = KtPsiFactory(project).createExpressionByPattern("$0 != null", conditionLhs)
    return convertToIfStatement(condition, thenClause, elseClause)
}

fun KtExpression.convertToIfStatement(
    condition: KtExpression,
    thenClause: KtExpression,
    elseClause: KtExpression? = null
): KtIfExpression =
    runWriteAction { replaced(KtPsiFactory(project).createIf(condition, thenClause, elseClause)) }

fun KtExpression.convertToIfNullExpression(conditionLhs: KtExpression, thenClause: KtExpression): KtIfExpression {
    val condition = KtPsiFactory(project).createExpressionByPattern("$0 == null", conditionLhs)
    return this.convertToIfStatement(condition, thenClause)
}
