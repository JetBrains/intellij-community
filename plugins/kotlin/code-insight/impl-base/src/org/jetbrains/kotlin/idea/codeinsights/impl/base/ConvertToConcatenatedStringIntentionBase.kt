// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getContentRange
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted

abstract class ConvertToConcatenatedStringIntentionBase : PsiUpdateModCommandAction<KtStringTemplateExpression>(
    KtStringTemplateExpression::class.java,
) {
    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation? =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun getFamilyName(): @IntentionFamilyName String {
        return KotlinBundle.message("convert.template.to.concatenated.string")
    }

    override fun isElementApplicable(element: KtStringTemplateExpression, context: ActionContext): Boolean {
        if (element.lastChild.node.elementType != KtTokens.CLOSING_QUOTE) return false // not available for unclosed literal
        return element.entries.any { it is KtStringTemplateEntryWithExpression }
    }

    override fun invoke(context: ActionContext, element: KtStringTemplateExpression, updater: ModPsiUpdater) {
        checkNotNull(element.text) { "Failed to get template expression's text" }
        val psiFactory = KtPsiFactory(element.project)
        val oldPrefixLength = element.entryPrefixLength

        val entries = element.entries.filterNot { it is KtStringTemplateEntryWithExpression && it.expression == null }
        val convertFirstEntryExplicitly = entries.firstOrNull().needsExplicitToStringConversion()
        val unmergedOperandsQueue = ArrayDeque<KtExpression>()

        entries.flatMapTo(unmergedOperandsQueue) { topLevelEntry ->
            topLevelEntry.toOperandExpressions(psiFactory, oldPrefixLength, isSingleQuoted = element.isSingleQuoted())
        }
        if (convertFirstEntryExplicitly) convertFirstToString(unmergedOperandsQueue, psiFactory)

        val mergedOperands = mergeStringGroups(unmergedOperandsQueue, psiFactory)
        val replacement = psiFactory
            .createExpression(mergedOperands.joinToString(separator = " + ") { it.text })
            .safeDeparenthesizeOperands()
        element.replace(replacement)
    }

    private fun KtExpression.safeDeparenthesizeOperands(): KtExpression {
        if (this is KtBinaryExpression) {
            val deparenthesizedLeft = this.left!!.safeDeparenthesizeOperands()
            val deparenthesizedRight = this.right!!.safeDeparenthesizeOperands()
            return KtPsiFactory(this.project).createExpressionByPattern(
                "$0+$1",
                deparenthesizedLeft.text,
                deparenthesizedRight.text
            )
        }
        if (this is KtParenthesizedExpression && KtPsiUtil.areParenthesesUseless(this)) {
            return KtPsiUtil.safeDeparenthesize(this, true)
        }
        return this
    }

    private fun KtStringTemplateEntry?.needsExplicitToStringConversion(): Boolean {
        if (this !is KtStringTemplateEntryWithExpression) return false
        val expression = expression ?: return false
        return !isExpressionOfStringType(expression)
                || expression is KtStringTemplateExpression && expression.entries.firstOrNull().needsExplicitToStringConversion()
    }

    private fun KtStringTemplateEntry.toOperandExpressions(
        psiFactory: KtPsiFactory,
        oldPrefixLength: Int,
        isSingleQuoted: Boolean,
    ): List<KtExpression> {
        return when {
            this is KtStringTemplateEntryWithExpression -> {
                val expression = expression ?: return emptyList()
                if (expression is KtStringTemplateExpression) {
                    expression.entries
                        .filterNot { it is KtStringTemplateEntryWithExpression && it.expression == null }
                        .flatMap { nestedEntry ->
                            nestedEntry.toOperandExpressions(
                                psiFactory,
                                oldPrefixLength = expression.entryPrefixLength,
                                isSingleQuoted = expression.isSingleQuoted(),
                            )
                        }
                } else {
                    val text = if (needsParentheses(expression)) "(${expression.text})" else expression.text
                    listOf(psiFactory.createExpression(text))
                }
            }

            else -> listOf(createStringTemplate(psiFactory, text, oldPrefixLength, isMultiQuoted = !isSingleQuoted))
        }
    }

    private fun createStringTemplate(
        psiFactory: KtPsiFactory, content: String, prefixLength: Int, isMultiQuoted: Boolean
    ): KtStringTemplateExpression = when {
        prefixLength > 1 -> psiFactory.createMultiDollarStringTemplate(content, prefixLength, forceMultiQuoted = isMultiQuoted)
        isMultiQuoted -> psiFactory.createRawStringTemplate(content)
        else -> psiFactory.createStringTemplate(content)
    }

    private fun mergeStringGroups(unmergedOperandsQueue: ArrayDeque<KtExpression>, psiFactory: KtPsiFactory): List<KtExpression> {
        val mergedOperands = mutableListOf<KtExpression>()
        while (unmergedOperandsQueue.isNotEmpty()) {
            val nextOperand = unmergedOperandsQueue.removeFirst()
            when {
                nextOperand is KtStringTemplateExpression -> {
                    val mergedStringGroup = mergeNextSequentialStrings(nextOperand, unmergedOperandsQueue, psiFactory)
                    mergedOperands.add(mergedStringGroup)
                }
                else -> mergedOperands.add(nextOperand)
            }
        }

        return mergedOperands
    }

    private fun mergeNextSequentialStrings(
        firstString: KtStringTemplateExpression,
        unmergedOperandsQueue: ArrayDeque<KtExpression>,
        psiFactory: KtPsiFactory,
    ): KtStringTemplateExpression {
        val group = findNextStringGroup(firstString, unmergedOperandsQueue)
        val concatenatedText = group.joinToString(separator = "") {
            it.getContentRange().substring(it.text)
        }
        val tempStringForPrefixEstimation = createStringTemplate(
            psiFactory, concatenatedText, prefixLength = 1, isMultiQuoted = !firstString.isSingleQuoted()
        )
        // all interpolations have been split into individual expressions, strings contain only text by now
        val prefixLength = findPrefixLengthForPlainTextConversion(tempStringForPrefixEstimation)
        val merged = createStringTemplate(
            psiFactory, concatenatedText, prefixLength = prefixLength, isMultiQuoted = !firstString.isSingleQuoted()
        )
        return merged
    }

    private fun findNextStringGroup(
        firstString: KtStringTemplateExpression,
        unmergedOperandsQueue: ArrayDeque<KtExpression>,
    ): MutableList<KtStringTemplateExpression> {
        val group = mutableListOf(firstString)

        @Suppress("UNCHECKED_CAST")
        val nextAcceptableStrings = unmergedOperandsQueue.takeWhile { next ->
            next is KtStringTemplateExpression && next.isSingleQuoted() == firstString.isSingleQuoted()
        } as List<KtStringTemplateExpression>

        group.addAll(nextAcceptableStrings)
        repeat(nextAcceptableStrings.size) { unmergedOperandsQueue.removeFirst() }

        return group
    }

    private fun convertFirstToString(unmergedOperandsQueue: ArrayDeque<KtExpression>, psiFactory: KtPsiFactory) {
        if (unmergedOperandsQueue.isEmpty()) return
        val first = unmergedOperandsQueue.removeFirst()
        unmergedOperandsQueue.addFirst(psiFactory.createExpression("${first.text}.toString()"))
    }

    private fun needsParentheses(expression: KtExpression): Boolean = when (expression) {
        is KtPostfixExpression -> false
        is KtAnnotatedExpression,
        is KtLabeledExpression,
        is KtOperationExpression -> true

        is KtIfExpression -> expression.`else` !is KtBlockExpression
        else -> false
    }

    /**
     * Tells whether given [expression] is of the string type.
     *
     * Called from mod command invoke that will be executed on a background thread.
     */
    abstract fun isExpressionOfStringType(expression: KtExpression): Boolean
}
