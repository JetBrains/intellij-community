// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.psi.KtBlockStringTemplateEntry
import org.jetbrains.kotlin.psi.KtEscapeStringTemplateEntry
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtSimpleNameStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntry
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression

val dollarLiteralExpressions: Array<String> = arrayOf(
    "'$'", "\"$\""
)

private object PrefixedStringTemplateUtils

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
    return if (newPrefixLength == 0) {
        if (isDestinationSingleQuoted) {
            factory.createStringTemplate(contentText)
        } else {
            // hack until KtPsiFactory has a triple-quoted template generation
            factory.createExpression("\"\"\"$contentText\"\"\"") as KtStringTemplateExpression
        }
    } else {
        factory.createMultiDollarStringTemplate(contentText, newPrefixLength, forceMultiQuoted = !isDestinationSingleQuoted)
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

internal fun KtStringTemplateEntry.asOneToOneDiff(newText: String): EntryUpdateDiff {
    return EntryUpdateDiff(0..<textLength, text, newText)
}

fun KtStringTemplateEntryWithExpression.changePrefixLength(prefixLength: Int): KtStringTemplateEntryWithExpression {
    require(prefixLength > 0) { "Unexpected string template prefix length: $prefixLength" }

    val replacement = when (this) {
        is KtSimpleNameStringTemplateEntry -> changePrefixLength(prefixLength)
        is KtBlockStringTemplateEntry -> changePrefixLength(prefixLength)
        else -> this
    }

    return replacement
}

fun KtBlockStringTemplateEntry.changePrefixLength(prefixLength: Int): KtStringTemplateEntryWithExpression {
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

fun KtSimpleNameStringTemplateEntry.changePrefixLength(prefixLength: Int): KtSimpleNameStringTemplateEntry {
    require(prefixLength > 0) { "Unexpected string template entry prefix length: $prefixLength" }
    val ktPsiFactory = KtPsiFactory(project)
    return ktPsiFactory.createMultiDollarSimpleNameStringTemplateEntry(
        expression?.text.orEmpty(),
        prefixLength = prefixLength,
    )
}

fun KtLiteralStringTemplateEntry.escapeIfNecessary(
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

fun KtLiteralStringTemplateEntry.escapeSpecialCharacters(): List<EntryUpdateDiff> {
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

fun KtStringTemplateEntry.unescapeIfPossible(newPrefixLength: Int): KtStringTemplateEntry {
    fun previousDollarsCount(): Int {
        if (this.prevSibling !is KtLiteralStringTemplateEntry) return 0
        return this.prevSibling.text.takeLastWhile { it == '$' }.length
    }

    return when (this) {
        is KtEscapeStringTemplateEntry -> {
            if (this.unescapedValue != "$") return this
            if (previousDollarsCount() + 1 >= newPrefixLength
                && (nextSibling as? KtLiteralStringTemplateEntry)?.canBeConsideredIdentifierOrBlock() == true
            ) return this
            KtPsiFactory(project).createLiteralStringTemplateEntry("$")
        }
        is KtBlockStringTemplateEntry -> {
            val expression = this.expression ?: return this
            if (expression.text !in dollarLiteralExpressions) return this
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
fun KtLiteralStringTemplateEntry.canBeConsideredIdentifierOrBlock(): Boolean {
    val firstChar = text.firstOrNull() ?: return false
    return firstChar.isLetter() || firstChar == '_' || firstChar == '{' || firstChar == '`'
}
