// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.k2.codeinsight.intentions.multiDollarStrings

import org.jetbrains.kotlin.idea.codeinsights.impl.base.canBeConsideredIdentifierOrBlock
import org.jetbrains.kotlin.idea.codeinsights.impl.base.changePrefixLength
import org.jetbrains.kotlin.idea.codeinsights.impl.base.dollarLiteralExpressions
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import org.jetbrains.kotlin.psi.psiUtil.plainContent

private const val DEFAULT_INTERPOLATION_PREFIX_LENGTH: Int = 2
private const val INTERPOLATION_PREFIX_LENGTH_THRESHOLD: Int = 5

private const val DOLLAR: String = "$"

/**
 * Convert a string to a multi-dollar string, choosing an appropriate prefix length based on the string's content.
 * The function searches for the shortest possible prefix that doesn't exceed [INTERPOLATION_PREFIX_LENGTH_THRESHOLD].
 * If no such prefix exists, the [DEFAULT_INTERPOLATION_PREFIX_LENGTH] is used.
 */
internal fun convertToMultiDollarString(element: KtStringTemplateExpression): KtStringTemplateExpression {
    require(element.interpolationPrefix == null) { "Can't convert the string which already has a prefix to multi-dollar string" }

    val longestUnsafeDollarSequence = longestUnsafeDollarSequenceLength(element, threshold = INTERPOLATION_PREFIX_LENGTH_THRESHOLD)
    val prefixLength = if (longestUnsafeDollarSequence in DEFAULT_INTERPOLATION_PREFIX_LENGTH..< INTERPOLATION_PREFIX_LENGTH_THRESHOLD)
        longestUnsafeDollarSequence + 1 else DEFAULT_INTERPOLATION_PREFIX_LENGTH

    replaceExpressionEntries(element, prefixLength)

    val replaced = element.replace(
        KtPsiFactory(element.project).createMultiDollarStringTemplate(
            content = element.plainContent,
            prefixLength = prefixLength,
            forceMultiQuoted = !element.isSingleQuoted(),
        )
    ) as KtStringTemplateExpression

    return replaced
}

/**
 * Replace dollar escape sequences in a string template if it's safe, i.e., if replacement won't turn a literal part into interpolation.
 * Both `\$` and `${'$'}` sequences are replaced if possible.
 */
internal fun simplifyDollarEntries(element: KtStringTemplateExpression): KtStringTemplateExpression {
    val ktPsiFactory = KtPsiFactory(element.project)
    val prefixLength = element.interpolationPrefix?.textLength?.takeIf { it > 1 } ?: return element

    for (entry in element.entries) {
        when (entry) {
            is KtEscapeStringTemplateEntry -> {
                if (entry.isEscapedDollar() && entry.isSafeToReplaceWithDollar(prefixLength))
                    entry.replace(ktPsiFactory.createLiteralStringTemplateEntry(DOLLAR))
            }

            is KtBlockStringTemplateEntry -> {
                if (entry.expression?.text in dollarLiteralExpressions && entry.isSafeToReplaceWithDollar(prefixLength))
                    entry.replace(ktPsiFactory.createLiteralStringTemplateEntry(DOLLAR))
            }
        }
    }

    val replacement = ktPsiFactory.createMultiDollarStringTemplate(
        content = element.plainContent,
        prefixLength = prefixLength,
        forceMultiQuoted = !element.isSingleQuoted(),
    )

    return element.replace(replacement) as KtStringTemplateExpression
}

internal fun longestUnsafeDollarSequenceLength(
    element: KtStringTemplateExpression,
    threshold: Int = Int.MAX_VALUE
): Int {
    var longest = 0
    var current = 0

    for (entry in element.entries) {
        when (entry) {
            is KtSimpleNameStringTemplateEntry -> {
                current = 0
            }

            is KtEscapeStringTemplateEntry -> {
                if (entry.isEscapedDollar()) current++ else current = 0
            }

            is KtBlockStringTemplateEntry -> {
                when {
                    entry.isSimplifiableInterpolatedDollar() -> current++
                    else -> {
                        current = 0
                    }
                }
            }

            is KtLiteralStringTemplateEntry -> {
                when {
                    entry.canBeConsideredIdentifierOrBlock() -> {
                        if (current > longest) longest = current
                        if (longest >= threshold) break
                        current = entry.text.takeLastWhile { it == '$' }.length
                    }

                    entry.text.all { it == '$' } -> {
                        current += entry.text.length
                    }

                    entry.text.endsWith('$') -> {
                        current = entry.text.takeLastWhile { it == '$' }.length
                    }

                    else -> current = 0
                }
            }
        }
    }

    return longest
}

private fun KtBlockStringTemplateEntry.isSimplifiableInterpolatedDollar(): Boolean {
    return this.expression?.text in dollarLiteralExpressions
}

private fun replaceExpressionEntries(stringTemplate: KtStringTemplateExpression, prefixLength: Int) {
    for (entry in stringTemplate.entries) {
        if (entry is KtStringTemplateEntryWithExpression) {
            entry.replace(entry.changePrefixLength(prefixLength))
        }
    }
}

private fun KtEscapeStringTemplateEntry.isEscapedDollar(): Boolean = unescapedValue == DOLLAR

/**
 * It's unsafe to replace with a `$` if the part before the entry ends with a `$`, and the part after can be considered identifier/block.
 * By the time we check the entry, previous siblings will have been replaced with dollar literals if that is possible.
 */
private fun KtStringTemplateEntry.isSafeToReplaceWithDollar(prefixLength: Int): Boolean {
    if (prevSibling !is KtLiteralStringTemplateEntry) return true
    val nextSiblingStringLiteral = nextSibling as? KtLiteralStringTemplateEntry ?: return true
    if (!nextSiblingStringLiteral.canBeConsideredIdentifierOrBlock()) return true
    val trailingDollarsLength = prevSibling.text.takeLastWhile { it.toString() == DOLLAR }.length
    return trailingDollarsLength + 1 < prefixLength
}
