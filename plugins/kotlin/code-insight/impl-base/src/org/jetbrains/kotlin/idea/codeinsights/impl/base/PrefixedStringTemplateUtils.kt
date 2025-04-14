// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isSingleQuoted
import org.jetbrains.kotlin.psi.psiUtil.plainContent

private const val DEFAULT_INTERPOLATION_PREFIX_LENGTH: Int = 2
private const val INTERPOLATION_PREFIX_LENGTH_THRESHOLD: Int = 5

val dollarLiteralExpressions: Array<String> = arrayOf(
    "'$'", "\"$\""
)

/**
 * Convert this string template to a new one with the specified interpolation prefix length.
 * The content of the template does not change.
 * If the template already has the required prefix, then it is returned as is.
 * Otherwise, a new template will be created with all its interpolation entries converted to the new prefix.
 * The function is not recursive — it doesn't update nested template expressions.
 * In case the passed template contains syntax errors and cannot be recreated with a different prefix, it will be returned unchanged.
 */
fun KtStringTemplateExpression.changeInterpolationPrefix(
    newPrefixLength: Int,
    isSourceSingleQuoted: Boolean,
    isDestinationSingleQuoted: Boolean,
    onEntryUpdate: (StringTemplateEntryUpdateInfo) -> Unit = {},
): KtStringTemplateExpression {
    require(newPrefixLength >= 0) { "Unexpected string template prefix length: $newPrefixLength" }
    if (this.interpolationPrefix?.textLength == newPrefixLength) return this
    val factory = KtPsiFactory(project)
    val contentText = buildString {
        for ((index, entry) in entries.withIndex()) {
            val newPrefixLength = maxOf(newPrefixLength, 1)
            val newEntries: List<EntryUpdateDiff> = when (entry) {
                // prefix length 0 means non-prefixed string — its entries start with a single $
                is KtStringTemplateEntryWithExpression -> {
                    val newEntry = entry.changePrefixLength(newPrefixLength).unescapeIfPossible(newPrefixLength)
                    listOf(entry.asOneToOneDiff(newEntry.text))
                }
                is KtLiteralStringTemplateEntry -> {
                    entry.escapeIfNecessary(newPrefixLength, isSourceSingleQuoted, isDestinationSingleQuoted)
                }
                is KtEscapeStringTemplateEntry -> {
                    listOf(entry.unescapeIfPossible(newPrefixLength)).map { unescapedEntry ->
                        entry.asOneToOneDiff(unescapedEntry.text)
                    }
                }
                else -> {
                    listOf(entry.asOneToOneDiff(entry.text))
                }
            }
            val newText = newEntries.joinToString(separator = "") { it.newText }
            append(newText)
            if (newText != entry.text) {
                onEntryUpdate(StringTemplateEntryUpdateInfo(index, entry, newEntries))
            }
        }
    }
    return when {
        newPrefixLength != 0 ->
            factory.createMultiDollarStringTemplate(contentText, newPrefixLength, forceMultiQuoted = !isDestinationSingleQuoted)
        isDestinationSingleQuoted -> factory.createStringTemplate(contentText)
        else -> factory.createRawStringTemplate(contentText)
    }
}

class StringTemplateEntryUpdateInfo(
    val index: Int,
    val oldEntry: KtStringTemplateEntry,
    val diffs: List<EntryUpdateDiff>,
) {
    override fun toString(): String {
        return """
            "${oldEntry.text}"
            ${diffs.joinToString("\n")}
        """.trimIndent()
    }
}

class EntryUpdateDiff(
    val oldRange: IntRange,
    val oldText: String,
    val newText: String,
) {
    override fun toString(): String {
        return """$oldRange: "$oldText" -> "$newText""""
    }
}

private fun KtStringTemplateEntry.asOneToOneDiff(newText: String): EntryUpdateDiff {
    return EntryUpdateDiff(0..<textLength, text, newText)
}

private fun KtStringTemplateEntryWithExpression.changePrefixLength(prefixLength: Int): KtStringTemplateEntryWithExpression {
    require(prefixLength > 0) { "Unexpected string template prefix length: $prefixLength" }

    val replacement = when (this) {
        is KtSimpleNameStringTemplateEntry -> changePrefixLength(prefixLength)
        is KtBlockStringTemplateEntry -> changePrefixLength(prefixLength)
        else -> this
    }

    return replacement
}

private fun KtBlockStringTemplateEntry.changePrefixLength(prefixLength: Int): KtStringTemplateEntryWithExpression {
    require(prefixLength > 0) { "Unexpected string template entry prefix length: $prefixLength" }
    val ktPsiFactory = KtPsiFactory(project)
    val expression = this.expression
    val replacement = if (expression != null) {
        ktPsiFactory.createMultiDollarBlockStringTemplateEntry(
            expression,
            prefixLength = prefixLength,
        )
    } else {
        // In case of incomplete code with no KtExpression inside the block, create a replacement from scratch
        val prefix = "$".repeat(prefixLength)
        val incompleteExpression = ktPsiFactory.createExpression("$prefix\"$prefix{}\"")
        (incompleteExpression as KtStringTemplateExpression).entries.single() as KtBlockStringTemplateEntry
    }

    return replacement
}

private fun KtSimpleNameStringTemplateEntry.changePrefixLength(prefixLength: Int): KtSimpleNameStringTemplateEntry {
    require(prefixLength > 0) { "Unexpected string template entry prefix length: $prefixLength" }
    val ktPsiFactory = KtPsiFactory(project)
    return ktPsiFactory.createMultiDollarSimpleNameStringTemplateEntry(
        expression?.text.orEmpty(),
        prefixLength = prefixLength,
    )
}

private fun KtLiteralStringTemplateEntry.escapeIfNecessary(
    newPrefixLength: Int,
    isSourceSingleQuoted: Boolean,
    isDestinationSingleQuoted: Boolean,
): List<EntryUpdateDiff> {
    // relying on that $ literal PSI nodes are grouped and consist only of $ chars
    if (text.all { ch -> ch == '$' }) {
        return escapeDollarIfNecessary(newPrefixLength, isDestinationSingleQuoted)
    }
    if (!isSourceSingleQuoted && isDestinationSingleQuoted) return escapeSpecialCharacters()
    return listOf(this.asOneToOneDiff(text))
}

/**
 * If the literal string template entry doesn't contain $ chars or is safe to use with the [newPrefixLength], returns it as is.
 * Otherwise, create a new literal entry with an escaped last $.
 */
private fun KtLiteralStringTemplateEntry.escapeDollarIfNecessary(
    newPrefixLength: Int,
    isDestinationSingleQuoted: Boolean,
): List<EntryUpdateDiff> {
    val unchangedDiff = this.asOneToOneDiff(text)
    if (textLength < newPrefixLength) return listOf(unchangedDiff)
    val nextSibling = nextSibling as? KtLiteralStringTemplateEntry ?: return listOf(unchangedDiff)
    if (!nextSibling.canBeConsideredIdentifierOrBlock()) return listOf(unchangedDiff)

    val ktPsiFactory = KtPsiFactory(project)
    val escapedDollar = if (isDestinationSingleQuoted) """\$""" else "${"$".repeat(newPrefixLength)}{'$'}"
    val beforeLast = text.dropLast(1)
    val escapedLastDollar = ktPsiFactory.createStringTemplate(escapedDollar).entries.singleOrNull() ?: return listOf(unchangedDiff)
    return listOfNotNull(
        beforeLast.takeIf { it.isNotEmpty() }?.let { EntryUpdateDiff(0..<textLength - 1, it, it) },
        EntryUpdateDiff(textLength - 1..textLength, text.last().toString(), escapedLastDollar.text.orEmpty()),
    )
}

private fun KtLiteralStringTemplateEntry.escapeSpecialCharacters(): List<EntryUpdateDiff> {
    val escaper = StringUtil.escaper(true, "\"")
    var from = 0
    var to = 0
    var nextChunkBuilder = StringBuilder()
    val diffs = mutableListOf<EntryUpdateDiff>()

    fun dumpSimpleChunk() {
        if (from < to) {
            val simpleTextChunk = nextChunkBuilder.toString()
            nextChunkBuilder = StringBuilder()
            diffs.add(EntryUpdateDiff(from..<to, simpleTextChunk, simpleTextChunk))
        }
    }

    for (char in text) {
        val oldCharAsString = char.toString()
        val escapedCharAsString = escaper.apply(oldCharAsString)
        if (oldCharAsString == escapedCharAsString) {
            to++
            nextChunkBuilder.append(escapedCharAsString)
        } else {
            dumpSimpleChunk()
            diffs.add(EntryUpdateDiff(to..<to + 1, oldCharAsString, escapedCharAsString))
            to++
            from = to
        }
    }
    dumpSimpleChunk()

    return diffs
}

private fun KtStringTemplateEntry.unescapeIfPossible(newPrefixLength: Int): KtStringTemplateEntry {
    return when (this) {
        is KtEscapeStringTemplateEntry -> {
            if (this.unescapedValue != "$") return this
            if (!isSafeToReplaceWithDollar(newPrefixLength)) return this
            KtPsiFactory(project).createLiteralStringTemplateEntry("$")
        }
        is KtBlockStringTemplateEntry -> {
            val expression = this.expression ?: return this
            if (expression.text !in dollarLiteralExpressions) return this
            if (!isSafeToReplaceWithDollar(newPrefixLength)) return this
            KtPsiFactory(project).createLiteralStringTemplateEntry("$")
        }
        else -> this
    }
}

/**
 * ```
 * Identifier
 *   : (Letter | '_') (Letter | '_' | UnicodeDigit)*
 *   | '`' ~([\r\n] | '`')+ '`'
 *   ;
 * ```
 *
 * The function can give false positives in corner cases when a backtick after `$` has no matching closing backtick.
 * This tradeoff allows avoiding potentially complicated and error-prone text searches in the file.
 * The closing backtick is not limited by the same string template and can come from various places of the PSI tree.
 * E.g., in the following case there are two backticks in different expressions that would be part of one string without escaping.
 * ```
 * println("\$`"); println("`")
 * ```
 */
fun KtLiteralStringTemplateEntry.canBeConsideredIdentifierOrBlock(): Boolean =
    text.firstOrNull()?.canBeStartOfIdentifierOrBlock() == true

fun Char.canBeStartOfIdentifierOrBlock(): Boolean {
    return isLetter() || this == '_' || this == '{' || this == '`'
}

fun KtStringTemplateEntry.isEscapedDollar(): Boolean = when (this) {
    is KtEscapeStringTemplateEntry -> this.isEscapedDollar()
    is KtBlockStringTemplateEntry -> this.isInterpolatedDollarLiteralExpression()
    else -> false
}

fun KtStringTemplateExpression.findTextRangesInParentForEscapedDollars(includeUnsafe: Boolean): List<TextRange> {
    return entries.filter { entry ->
        entry.isEscapedDollar() && (includeUnsafe || entry.isSafeToReplaceWithDollar(entryPrefixLength))
    }.map { it.textRangeInParent }
}

/**
 * Context for the multi-dollar conversion inspection and intention.
 */
class MultiDollarConversionInfo(
    val prefixLength: Int,
)

fun prepareMultiDollarConversionInfo(element: KtStringTemplateExpression, useFallbackPrefix: Boolean): MultiDollarConversionInfo? {
    val suitablePrefixLength = findSuitablePrefixLength(element, useFallbackPrefix) ?: return null
    return MultiDollarConversionInfo(suitablePrefixLength)
}

/**
 * Search for the shortest possible prefix that doesn't exceed [INTERPOLATION_PREFIX_LENGTH_THRESHOLD].
 * If no such prefix exists, the [DEFAULT_INTERPOLATION_PREFIX_LENGTH] if [useFallbackPrefix] is `true`, or `null` otherwise.
 */
private fun findSuitablePrefixLength(element: KtStringTemplateExpression, useFallbackPrefix: Boolean): Int? {
    val longestUnsafeDollarSequence = longestUnsafeDollarSequenceLengthForConservativeConversion(
        element, INTERPOLATION_PREFIX_LENGTH_THRESHOLD
    )
    if (longestUnsafeDollarSequence >= INTERPOLATION_PREFIX_LENGTH_THRESHOLD) {
        return if (useFallbackPrefix) DEFAULT_INTERPOLATION_PREFIX_LENGTH else null
    }
    return maxOf(longestUnsafeDollarSequence + 1, DEFAULT_INTERPOLATION_PREFIX_LENGTH)
}

/**
 * Convert a plain string to a multi-dollar string with the specified prefix length
 */
fun convertToMultiDollarString(element: KtStringTemplateExpression, contextInfo: MultiDollarConversionInfo): KtStringTemplateExpression {
    require(element.interpolationPrefix == null) { "Can't convert the string which already has a prefix to multi-dollar string" }
    replaceExpressionEntries(element, contextInfo.prefixLength)

    val replaced = element.replace(
        KtPsiFactory(element.project).createMultiDollarStringTemplate(
            content = element.plainContent,
            prefixLength = contextInfo.prefixLength,
            forceMultiQuoted = !element.isSingleQuoted(),
        )
    ) as KtStringTemplateExpression

    return replaced
}

/**
 * Convert a multi-dollar string to a plain string with the same quotes.
 *
 * @return the replacement string without the interpolation prefix or the original string if it doesn't have a prefix
 */
fun convertToStringWithoutPrefix(element: KtStringTemplateExpression): KtStringTemplateExpression {
    if (element.interpolationPrefix == null) return element

    replaceExpressionEntries(element, 1)
    val psiFactory = KtPsiFactory(element.project)
    val replaced = psiFactory.createStringTemplate(
        content = element.toEscapedText(element.isSingleQuoted()),
        prefixLength = 0,
        isRaw = !element.isSingleQuoted(),
    )
    return element.replace(replaced) as KtStringTemplateExpression
}

private fun KtStringTemplateExpression.toEscapedText(isSingleQuoted: Boolean): String {
    val dollarReplacement = if (isSingleQuoted) """\$""" else "\${'$'}"
    return entries.joinToString(separator = "") { entry ->
        if (entry is KtLiteralStringTemplateEntry) {
            entry.text.replace("$", dollarReplacement)
        } else {
            entry.text
        }
    }
}

/**
 * Replace dollar escape sequences in a string template if it's safe, i.e., if replacement won't turn a literal part into interpolation.
 * Both `\$` and `${'$'}` sequences are replaced if possible.
 */
fun simplifyDollarEntries(element: KtStringTemplateExpression): KtStringTemplateExpression {
    val ktPsiFactory = KtPsiFactory(element.project)
    val entryPrefixLength = element.entryPrefixLength

    for (entry in element.entries) {
        when (entry) {
            is KtEscapeStringTemplateEntry -> {
                if (entry.isEscapedDollar() && entry.isSafeToReplaceWithDollar(entryPrefixLength))
                    entry.replace(ktPsiFactory.createLiteralStringTemplateEntry("$"))
            }

            is KtBlockStringTemplateEntry -> {
                if (entry.expression?.text in dollarLiteralExpressions && entry.isSafeToReplaceWithDollar(entryPrefixLength))
                    entry.replace(ktPsiFactory.createLiteralStringTemplateEntry("$"))
            }
        }
    }

    val replacement = ktPsiFactory.createStringTemplate(
        element.plainContent, element.templatePrefixLength, isRaw = !element.isSingleQuoted()
    )

    return element.replace(replacement) as KtStringTemplateExpression
}

/**
 * Finds prefix length for a conversion that doesn't preserve interpolation entries.
 * In other words, the prefix length with which the string will only contain plain text after the conversion.
 */
fun findPrefixLengthForPlainTextConversion(element: KtStringTemplateExpression): Int {
    return longestUnsafeDollarSequenceLengthForPlainTextConversion(element) + 1
}

private fun longestUnsafeDollarSequenceLengthForConservativeConversion(
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
                    entry.isInterpolatedDollarLiteralExpression() -> current++
                    else -> {
                        current = 0
                    }
                }
            }
            is KtLiteralStringTemplateEntry -> {
                when {
                    entry.canBeConsideredIdentifierOrBlock() -> {
                        if (current > longest) longest = current
                        current = entry.trailingDollarsLength
                    }
                    entry.text.all { it == '$' } -> {
                        current += entry.text.length
                    }
                    entry.text.endsWith('$') -> {
                        current = entry.trailingDollarsLength
                    }
                    else -> current = 0
                }
            }
        }
        if (longest >= threshold) break
    }

    return longest
}

private fun longestUnsafeDollarSequenceLengthForPlainTextConversion(
    element: KtStringTemplateExpression,
    threshold: Int = Int.MAX_VALUE
): Int {
    var longest = 0
    var current = 0

    val entryPrefixLength = element.entryPrefixLength

    for (entry in element.entries) {
        when (entry) {
            is KtSimpleNameStringTemplateEntry,
            is KtBlockStringTemplateEntry -> {
                current += entryPrefixLength
                if (current > longest) longest = current
                current = 0
            }
            is KtEscapeStringTemplateEntry -> {
                current = 0
            }
            is KtLiteralStringTemplateEntry -> {
                when {
                    entry.canBeConsideredIdentifierOrBlock() -> {
                        if (current > longest) longest = current
                        current = entry.trailingDollarsLength
                    }
                    entry.text.all { it == '$' } -> {
                        current += entry.text.length
                    }
                    entry.text.endsWith('$') -> {
                        current = entry.trailingDollarsLength
                    }
                    else -> {
                        current = 0
                    }
                }
            }
        }
        if (longest >= threshold) break
    }

    return longest
}

private fun KtBlockStringTemplateEntry.isInterpolatedDollarLiteralExpression(): Boolean {
    return this.expression?.text in dollarLiteralExpressions
}

private fun replaceExpressionEntries(stringTemplate: KtStringTemplateExpression, prefixLength: Int) {
    for (entry in stringTemplate.entries) {
        if (entry is KtStringTemplateEntryWithExpression) {
            entry.replace(entry.changePrefixLength(prefixLength))
        }
    }
}

private fun KtEscapeStringTemplateEntry.isEscapedDollar(): Boolean = unescapedValue == "$"

/**
 * It's unsafe to replace with a `$` if the part before the entry ends with a `$`, and the part after can be considered identifier/block.
 * By the time we check the entry, previous siblings will have been replaced with dollar literals if that is possible.
 */
private fun KtStringTemplateEntry.isSafeToReplaceWithDollar(prefixLength: Int): Boolean {
    val nextSiblingStringLiteral = nextSibling as? KtLiteralStringTemplateEntry ?: return true
    if (!nextSiblingStringLiteral.canBeConsideredIdentifierOrBlock()) return true
    val trailingDollarsLength = countTrailingDollarsInPreviousEntries()
    return trailingDollarsLength + 1 < prefixLength
}

private fun KtStringTemplateEntry.countTrailingDollarsInPreviousEntries(): Int {
    var count = 0
    var prev = prevSibling
    while (prev is KtLiteralStringTemplateEntry) {
        val trailingDollarsLength = prev.text.takeLastWhile { it.toString() == "$" }.length
        count += trailingDollarsLength
        if (prev.textLength == trailingDollarsLength) {
            prev = prev.prevSibling
        } else break
    }
    return count
}

private val KtStringTemplateEntry.trailingDollarsLength: Int
    get() = text.takeLastWhile { it.toString() == "$" }.length

val KtStringTemplateExpression.templatePrefixLength: Int
    get() = interpolationPrefix?.textLength ?: 0

val KtStringTemplateExpression.entryPrefixLength: Int
    get() = interpolationPrefix?.textLength ?: 1

/**
 * A utility dispatcher for creating string templates
 *
 * @param content string template content
 * @param prefixLength interpolation prefix length, `0` for no prefix
 * @param isRaw `true` for raw triple-quoted string, `false` for normal, single-quoted string
 */
fun KtPsiFactory.createStringTemplate(
    content: String,
    prefixLength: Int,
    isRaw: Boolean,
): KtStringTemplateExpression = when {
    prefixLength > 0 -> createMultiDollarStringTemplate(content, prefixLength, forceMultiQuoted = isRaw)
    isRaw -> createRawStringTemplate(content)
    else -> createStringTemplate(content)
}
