// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeInsight.intentions.shared

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.idea.base.psi.replaced
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.intentions.KotlinApplicableModCommandAction
import org.jetbrains.kotlin.idea.codeinsight.utils.callExpression
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForReceiver

// Difference in length between the raw strings marker (""") and the ordinary strings marker (")
private const val QUOTE_LENGTH_DIFFERENCE = 2

// Symbols with escaping:
private const val QUOTE = "\""
private const val ESCAPED_QUOTE = "\\\""
private const val ONE_SLASH = "\\"
private const val TWO_SLASHES = "\\\\"
private const val TRIPLE_QUOTE = "\"\"\""

internal class ToOrdinaryStringLiteralIntention :
    KotlinApplicableModCommandAction<KtStringTemplateExpression, Unit>(KtStringTemplateExpression::class) {

    override fun getFamilyName(): @IntentionFamilyName String =
        KotlinBundle.message("convert.to.ordinary.string.literal")

    override fun isApplicableByPsi(element: KtStringTemplateExpression): Boolean {
        return element.text.startsWith(TRIPLE_QUOTE)
    }

    override fun KaSession.prepareContext(element: KtStringTemplateExpression): Unit = Unit

    override fun getPresentation(context: ActionContext, element: KtStringTemplateExpression): Presentation =
        Presentation.of(familyName).withPriority(PriorityAction.Priority.LOW)

    override fun invoke(
        actionContext: ActionContext,
        element: KtStringTemplateExpression,
        elementContext: Unit,
        updater: ModPsiUpdater,
    ) {
        val startOffset = element.textRange.startOffset
        val endOffset = element.textRange.endOffset

        val entries = element.entries
        val trimIndentCall = getTrimIndentCall(element, entries)
        val newText = getNewText(trimIndentCall, entries)
        val psiFactory = KtPsiFactory(element.project)
        val newElement = psiFactory.createExpression(newText)

        val replaced = (trimIndentCall?.qualifiedExpression ?: element).replaced(newElement)
        moveCaret(actionContext, startOffset, updater, endOffset, replaced)
    }

    private class TrimIndentCall(
        val qualifiedExpression: KtQualifiedExpression,
        val stringTemplateText: String
    )

    /**
     * When the cursor points at a quote, it will point at the replaced version afterward.
     * In the rest of the cases it will keep pointing at the same character inside the string.
     */
    private fun moveCaret(
        actionContext: ActionContext,
        startOffset: Int,
        updater: ModPsiUpdater,
        endOffset: Int,
        replacedElement: KtExpression
    ) {
        val currentOffset = actionContext.offset
        val targetOffset = when {
            currentOffset - startOffset < QUOTE_LENGTH_DIFFERENCE -> startOffset
            endOffset - currentOffset < QUOTE_LENGTH_DIFFERENCE -> replacedElement.textRange.endOffset
            else -> currentOffset - QUOTE_LENGTH_DIFFERENCE
        }
        updater.moveCaretTo(targetOffset)
    }

    private fun getNewText(
        trimIndentCall: TrimIndentCall?,
        entries: Array<KtStringTemplateEntry>
    ): String = buildString {
        append(QUOTE)
        if (trimIndentCall != null) {
            append(trimIndentCall.stringTemplateText)
        } else {
            entries.joinTo(buffer = this, separator = "") {
                getTextFromStringTemplateEntry(it)
            }
        }
        append(QUOTE)
    }

    private fun getTextFromStringTemplateEntry(entry: KtStringTemplateEntry, escapeLineSeparators: Boolean = true): String {
        return if (entry is KtLiteralStringTemplateEntry) {
            entry.text.escape(escapeLineSeparators)
        } else {
            entry.text
        }
    }

    private fun String.escape(escapeLineSeparators: Boolean = true): String {
        var text = this
        text = text.replace(oldValue = ONE_SLASH, newValue = TWO_SLASHES)
        text = text.replace(oldValue = QUOTE, newValue = ESCAPED_QUOTE)
        return if (escapeLineSeparators) {
            text.escapeLineSeparators()
        } else {
            text
        }
    }

    private fun String.escapeLineSeparators(): String {
        return StringUtil.convertLineSeparators(/*text = */this, /*newSeparator = */"\\n")
    }

    private fun getTrimIndentCall(
        element: KtStringTemplateExpression,
        entries: Array<KtStringTemplateEntry>
    ): TrimIndentCall? {
        val qualifiedExpression = element.getQualifiedExpressionForReceiver()?.takeIf {
            it.callExpression?.isCalling(TRIM_INDENT_FUNCTIONS) == true
        } ?: return null

        val marginPrefix = if (qualifiedExpression.calleeName == "trimMargin") {
            getMarginPrefix(qualifiedExpression) ?: return null
        } else {
            null
        }
        val stringTemplateText = getStringTemplateText(entries, marginPrefix)
        return TrimIndentCall(qualifiedExpression, stringTemplateText)
    }

    private fun getMarginPrefix(qualifiedExpression: KtQualifiedExpression): String? {
        val valueArguments = qualifiedExpression.callExpression?.valueArguments
        return when (val arg = valueArguments?.singleOrNull()?.getArgumentExpression()) {
            null -> "|"

            is KtStringTemplateExpression -> {
                val entry = arg.entries.singleOrNull()
                entry?.takeIf { it is KtLiteralStringTemplateEntry }?.text
            }

            else -> null
        }
    }

    private fun getStringTemplateText(entries: Array<KtStringTemplateEntry>, marginPrefix: String?): String {
        return entries
            .joinToString(separator = "") {
                getTextFromStringTemplateEntry(entry = it, escapeLineSeparators = false)
            }
            .let {
                if (marginPrefix != null) {
                    it.trimMargin(marginPrefix)
                } else {
                    it.trimIndent()
                }
            }
            .escapeLineSeparators()
    }
}

private val KtQualifiedExpression.calleeName: String?
    get() = callExpression?.calleeExpression?.text

private fun KtCallExpression.isCalling(fqNames: List<FqName>): Boolean {
    val calleeText = calleeExpression?.text ?: return false
    return fqNames.any { it.shortName().asString() == calleeText }
}

private val TRIM_INDENT_FUNCTIONS: List<FqName> = listOf(
    FqName("kotlin.text.trimIndent"),
    FqName("kotlin.text.trimMargin")
)

