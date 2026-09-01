// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.formatter

import com.intellij.ide.todo.TodoConfiguration
import org.jetbrains.kotlin.idea.KotlinLanguage
import java.util.regex.Pattern

/**
 * Parser responsible for parsing KDocs and splitting the comment text into [Block]s and [Paragraph]s.
 * A block potentially starts with a tag and contains several lines that are split into paragraphs by the parser.
 * The parser allows for wrapping long lines of a paragraph to the next line.
 *
 * There are several settings that control how the parser behaves:
 *  - `WRAP_COMMENTS`: Determines whether to wrap long lines in the KDoc. Without this enabled, the parser does nothing.
 *  - `KDOC_PRESERVE_LINE_FEEDS`: Determines if custom line breaks within a paragraph are preserved.
 *
 * Mostly ported from the equivalent in Java: `com.intellij.psi.impl.source.codeStyle.javadoc.JDParser`
 */
internal class KDocFormattingParser(
    private val codeStyleSettings: KotlinCommonCodeStyleSettings,
) {

    fun wrapComment(text: String, indent: String): String? {
        if (!codeStyleSettings.WRAP_COMMENTS) return null
        // Note: `/**/` is not a KDoc comment
        if (!text.startsWith("/**") || !text.endsWith("*/") || text.length < 5) return null

        val rootSettings = codeStyleSettings.rootSettings
        val rightMargin = rootSettings.getRightMargin(KotlinLanguage.INSTANCE)
        val preserveLineFeeds = rootSettings.kotlinCustomSettings.KDOC_PRESERVE_LINE_FEEDS
        val wrapped = wrapKDocBody(text, rightMargin, indent, preserveLineFeeds)
        return wrapped.takeIf { it != text }
    }
}

/**
 * A KDoc line with its leading `*` removed.
 * [hadSeparatorSpace] is true whenever there was a space following the `*` before destarring.
 * Note that if [hadSeparatorSpace] is true, the single space separator is not included in [text].
 */
private data class DestarredLine(val text: String, val hadSeparatorSpace: Boolean)

/**
 * A line is a single line in a [Block].
 * If [isProtected] is true, the line is not wrapped.
 */
private data class Line(val text: String, val isProtected: Boolean, val hadSeparatorSpace: Boolean = true)

/**
 * A block is an inseparable unit of a KDoc element.
 * Each KDoc comment has at least one block, which is the starting block.
 * A new block (other than the starting block) is introduced by a line that starts with a tag.
 */
private data class Block(val tag: String?, val lines: MutableList<Line> = mutableListOf())

/**
 * A paragraph is a flowing piece of [text] that might span multiple lines and can potentially be wrapped.
 * If [isProtected] is true, the paragraph is not wrapped.
 */
private data class Paragraph(val text: String, val isProtected: Boolean, val hadSeparatorSpace: Boolean = true)

/**
 * A fence is a set of characters that introduce a special markdown/code block in a KDoc.
 * This class tracks the kind of fence and its length.
 */
private data class FenceInfo(val fenceChar: Char, val fenceLen: Int) {
    fun isClosedBy(line: String): Boolean {
        val closing = findCodeFence(line, opening = false) ?: return false
        return fenceChar == closing.fenceChar && closing.fenceLen >= fenceLen
    }
}

private fun wrapKDocBody(
    commentText: String,
    rightMargin: Int,
    indent: String = "",
    preserveLineFeeds: Boolean = true,
): String {
    val body = commentText.removePrefix("/**").removeSuffix("*/").trim()
    val wasMultiline = commentText.indexOf('\n') >= 0
    val blocks = splitIntoBlocks(destarLines(body))
    val wrappedBlocks = blocks.map { wrapBlock(it, rightMargin, indent, preserveLineFeeds) }
    return render(wrappedBlocks, indent, wasMultiline, rightMargin)
}

/**
 * Strips the leading `*` and a single leading space of KDoc comment lines.
 * Additionally, trailing whitespaces are also removed.
 */
private fun destarLines(body: String): List<DestarredLine> = body.split("\n").map { rawLine ->
    val withoutIndent = rawLine.trimStart()
    if (!withoutIndent.startsWith("*")) {
        // A line without the leading `*` gains one, so it also gains the conventional separator.
        return@map DestarredLine(withoutIndent.trimEnd(), hadSeparatorSpace = true)
    }
    val afterStar = withoutIndent.substring(1)
    val hadSeparatorSpace = afterStar.startsWith(" ")
    DestarredLine(afterStar.removePrefix(" ").trimEnd(), hadSeparatorSpace)
}


private const val INDENTED_CODE_WIDTH = 4

/**
 * Width of the leading indent, counting a tab as 4 columns.
 */
private fun indentWidth(line: String): Int {
    var width = 0
    for (ch in line) {
        when (ch) {
            ' ' -> width++
            '\t' -> width += INDENTED_CODE_WIDTH
            else -> return width
        }
    }
    return width
}

/**
 * Whether [line] starts a `TODO` item.
 * Note: `TODO`s are only recognized at the start of a line.
 */
private fun startsTodo(line: String, todoPatterns: List<Pattern>): Boolean {
    if (todoPatterns.isEmpty()) return false
    val trimmed = line.trim()
    return todoPatterns.any { it.matcher(trimmed).matches() }
}

/**
 * Splits destarred lines into an ordered list of blocks: a leading (possibly empty) description
 * block followed by one block per `@tag` occurrence, in original order.
 * A line is only treated as a tag boundary outside an open code fence.
 */
private fun splitIntoBlocks(destarredLines: List<DestarredLine>): List<Block> {
    val blocks = mutableListOf(Block(tag = null))
    // Patterns recognized to start TODO sections (e.g. `FIXME` and `TODO`)
    val todoPatterns = TodoConfiguration.getInstance().todoPatterns.mapNotNull { it.pattern }
    val multilineTodosEnabled = TodoConfiguration.getInstance().isMultiLine
    var currentFence: FenceInfo? = null
    var inIndentedCode = false
    var inTodo = false
    var todoIndent = 0
    var prevLineBlank = true

    for ((index, destarredLine) in destarredLines.withIndex()) {
        val (rawLine, hadSeparatorSpace) = destarredLine
        val fenceForThisLine = currentFence ?: findCodeFence(rawLine, opening = true)
        val isBlank = rawLine.isBlank()

        // A Markdown-indented code block opens on a line indented by at least four columns, but only
        // where it does not interrupt a paragraph, and it runs until a non-blank line removes the indent again.
        inIndentedCode = when {
            fenceForThisLine != null -> false
            isBlank -> inIndentedCode
            indentWidth(rawLine) >= INDENTED_CODE_WIDTH -> inIndentedCode || prevLineBlank
            else -> false
        }

        inTodo = when {
            isBlank -> false
            // A line matching a pattern ends the previous item and starts a new one
            startsTodo(rawLine, todoPatterns) -> {
                todoIndent = indentWidth(rawLine)
                true
            }
            // potential continuation of the TODO on a new line
            else -> multilineTodosEnabled && inTodo && indentWidth(rawLine) > todoIndent
        }

        val isProtected = fenceForThisLine != null ||
                (inIndentedCode && !isBlank) ||
                inTodo ||
                isSingleLineMarkdownConstruct(rawLine) ||
                isSetextHeadingLine(destarredLines, index)
        val startsNewTag = currentFence == null && rawLine.startsWith("@")

        if (startsNewTag) {
            val spaceIdx = rawLine.indexOfFirst { it == ' ' || it == '\t' }
            val tag = if (spaceIdx == -1) rawLine.substring(1) else rawLine.substring(1, spaceIdx)
            val rest = if (spaceIdx == -1) "" else rawLine.substring(spaceIdx).trim()
            val newBlock = Block(tag)
            newBlock.lines += Line(rest, isProtected = false)
            blocks += newBlock
        } else {
            blocks.last().lines += Line(rawLine, isProtected, hadSeparatorSpace)
        }

        currentFence = when {
            currentFence == null -> fenceForThisLine
            currentFence.isClosedBy(rawLine) -> null
            else -> currentFence
        }
        prevLineBlank = isBlank
    }
    return blocks
}

/**
 * Merges a block's lines into paragraphs.
 * Note that blank lines and fenced/code content stand alone and are never reflowed.
 * A Markdown construct line always starts a fresh paragraph.
 *
 * With [preserveLineFeeds] every line becomes its own paragraph, only lines exceeding the margin are wrapped.
 */
private fun splitIntoParagraphs(lines: List<Line>, preserveLineFeeds: Boolean): List<Paragraph> {
    val result = mutableListOf<Paragraph>()
    val sb = StringBuilder()

    fun flush() {
        if (sb.isNotEmpty()) {
            result += Paragraph(sb.toString(), isProtected = false)
            sb.setLength(0)
        }
    }

    for ((line, isProtectedLine, hadSeparatorSpace) in lines) {
        when {
            isProtectedLine -> {
                flush()
                result += Paragraph(line, isProtected = true, hadSeparatorSpace = hadSeparatorSpace)
            }

            line.isEmpty() -> {
                flush()
                result += Paragraph("", isProtected = false)
            }

            preserveLineFeeds -> result += Paragraph(line, isProtected = false, hadSeparatorSpace = hadSeparatorSpace)
            else -> {
                if (isStartOfMarkdownConstruct(line)) flush()
                if (sb.isEmpty()) {
                    // The line that opens a paragraph keeps its own indentation: for a nested list item or
                    // blockquote that indentation is structural, and trimming it would flatten the nesting.
                    sb.append(line)
                } else {
                    // Horizontal spacing loses its meaning once lines are fused into one paragraph.
                    sb.append(' ').append(line.trim())
                }
            }
        }
    }
    flush()
    return result
}

private fun wrapBlock(block: Block, rightMargin: Int, indent: String, preserveLineFeeds: Boolean): Block {
    val tagPrefixLength = if (block.tag != null) block.tag.length + 2 else 0 // "@" + tag + " "
    val continuationPrefixLength = indent.length + 3 // " * "
    val firstLineWidth = (rightMargin - continuationPrefixLength - tagPrefixLength).coerceAtLeast(1)
    val continuationWidth = (rightMargin - continuationPrefixLength).coerceAtLeast(1)

    val wrapped = mutableListOf<Line>()
    var isFirstLine = true
    for (paragraph in splitIntoParagraphs(block.lines, preserveLineFeeds)) {
        if (paragraph.isProtected || paragraph.text.isEmpty()) {
            wrapped += Line(paragraph.text, paragraph.isProtected, paragraph.hadSeparatorSpace)
            isFirstLine = false
            continue
        }
        var remaining = paragraph.text
        while (remaining.isNotEmpty()) {
            val width = if (isFirstLine) firstLineWidth else continuationWidth
            val wrapPos = computeWrapPosition(remaining, width)
            wrapped += Line(remaining.take(wrapPos), isProtected = false)
            remaining = remaining.drop(wrapPos + 1).trimStart() // + 1 to drop the space if it was there
            isFirstLine = false
        }
    }
    return block.copy(lines = wrapped)
}

/**
 * Computes the position where a line should be wrapped if it should be at most [width] in length.
 *
 * A line may only be broken at a space that is outside a link reference like `[Foo][Bar]` and outside an
 * inline code span like `val a = 5`.
 * When there is no such space before [width], the search continues past it, and the line is allowed to run
 * over the margin until the first opportunity is found.
 */
private fun computeWrapPosition(line: String, width: Int): Int {
    if (line.length <= width) return line.length

    var breakPoint = -1
    var bracketBalance = 0
    var codeSpanTicks = 0 // length of the opening backtick run, 0 when outside a code span

    var i = 0
    while (i < line.length && (i <= width || breakPoint < 0)) {
        val c = line[i]
        if (c == '`') {
            var runLength = 0
            // N backticks need to be closed by another N backticks after, so find out how many backticks there are
            while (i < line.length && line[i] == '`') {
                runLength++
                i++
            }
            // Either open or close a code block. Only close if the amount of backticks matches.
            when (codeSpanTicks) {
                0 -> codeSpanTicks = runLength
                runLength -> codeSpanTicks = 0
            }
            continue
        }
        when {
            codeSpanTicks > 0 -> {} // No bracket matching required inside code blocks
            c == '[' -> bracketBalance++
            bracketBalance > 0 && c == ']' -> bracketBalance--
            c == ' ' && bracketBalance == 0 && canWrapLineAt(line, i + 1) -> breakPoint = i
        }
        i++
    }

    return if (breakPoint > 0) breakPoint else line.length
}

/**
 * Checks whether the line can be wrapped at the given index.
 * If this function returns false, the line should be broken either at an earlier or later possible position.
 */
private fun canWrapLineAt(line: String, index: Int): Boolean {
    return !opensCodeFence(line, index)
}

/**
 * Whether the remainder of [line] starting at [index] would open a code fence if wrapped.
 * Wrapping there would turn into a code block, which changes semantics.
 */
private fun opensCodeFence(line: String, index: Int): Boolean {
    var start = index
    while (start < line.length && line[start].isWhitespace()) start++
    val ch = line.getOrNull(start)
    return (ch == '`' || ch == '~') && findCodeFence(line.substring(start), opening = true) != null
}

private fun render(
    wrappedBlocks: List<Block>,
    indent: String,
    wasMultiline: Boolean,
    rightMargin: Int,
): String {
    val physicalLines = mutableListOf<Line>()
    for ((tag, lines) in wrappedBlocks) {
        lines.forEachIndexed { idx, line ->
            physicalLines += when {
                idx != 0 || tag == null -> line
                line.text.isEmpty() -> line.copy(text = "@$tag", hadSeparatorSpace = true)
                else -> line.copy(text = "@$tag ${line.text}", hadSeparatorSpace = true)
            }
        }
    }
    // An empty multi-line KDoc needs to keep its empty line, while a single-line KDoc should not gain an additional line
    if (!wasMultiline && physicalLines.size == 1 && physicalLines[0].text.isEmpty()) physicalLines.clear()

    val canStayInline = !wasMultiline && physicalLines.size <= 1 &&
            indent.length + "/**  */".length + (physicalLines.firstOrNull()?.text?.length ?: 0) <= rightMargin

    if (canStayInline) {
        return if (physicalLines.isEmpty()) "/** */" else "/** ${physicalLines[0].text} */"
    }

    return buildString {
        append("/**\n")
        for ((text, _, hadSeparatorSpace) in physicalLines) {
            append(indent).append(" *")
            if (text.isNotEmpty()) {
                if (hadSeparatorSpace) {
                    append(' ')
                }
                append(text)
            }
            append('\n')
        }
        append(indent).append(" */")
    }
}

private fun isStartOfMarkdownConstruct(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed.startsWith(">") ||
            isThemeBreak(trimmed) ||
            isStartOfMarkdownHeader(trimmed) ||
            isMarkdownTableRow(trimmed) ||
            isStartOfMarkdownListItem(trimmed)
}

/**
 * Whether [line] is a Markdown construct in a line that should not be wrapped.
 * List items and blockquotes are deliberately excluded.
 */
private fun isSingleLineMarkdownConstruct(line: String): Boolean {
    val trimmed = line.trim()
    return isStartOfMarkdownHeader(trimmed) || isMarkdownTableRow(trimmed) || isThemeBreak(trimmed)
}

private fun isStartOfMarkdownHeader(line: String): Boolean = line.startsWith("#")

private fun isMarkdownTableRow(line: String): Boolean = line.startsWith("|") && line.count { it == '|' } > 1

/**
 * A theme break is three or more `-`, `_` or `*`, all of the same kind, and *nothing* else besides spacing.
 */
private fun isThemeBreak(line: String): Boolean {
    val marks = line.filterNot { it == ' ' || it == '\t' }
    if (marks.length < 3) return false
    val mark = marks[0]
    return (mark == '-' || mark == '_' || mark == '*') && marks.all { it == mark }
}

/**
 * Checks whether [line] could be a setext underline, which is the case if
 * it only consists of `-` or `=`.
 */
private fun isSetextUnderline(line: String): Boolean {
    val trimmed = line.trim()
    if (trimmed.isEmpty()) return false
    val mark = trimmed[0]
    return (mark == '=' || mark == '-') && trimmed.all { it == mark }
}

/**
 * Whether [line] can be the text an underline turns into a setext heading.
 */
private fun canBeSetextHeadingText(line: String?): Boolean =
    !line.isNullOrBlank() && !isSetextUnderline(line) && !isStartOfMarkdownConstruct(line)

/**
 * Whether the line at [index] is part of a setext heading, being either the underline itself or the text line
 * it underlines.
 */
private fun isSetextHeadingLine(lines: List<DestarredLine>, index: Int): Boolean {
    val line = lines[index].text
    val above = lines.getOrNull(index - 1)?.text
    val below = lines.getOrNull(index + 1)?.text
    return (isSetextUnderline(line) && canBeSetextHeadingText(above)) ||
            (canBeSetextHeadingText(line) && below != null && isSetextUnderline(below))
}

private val LIST_ITEM_PATTERN = Regex("^\\d+[).]")
private fun isStartOfMarkdownListItem(line: String): Boolean =
    line.startsWith("- ") || line.startsWith("+ ") || line.startsWith("* ") || LIST_ITEM_PATTERN.containsMatchIn(line)

/**
 * Detects an opening or closing Markdown code-block fence
 */
private fun findCodeFence(line: String, opening: Boolean): FenceInfo? {
    if (line.length < 3) return null

    var fenceFound = false
    var infoString = false
    var fenceLen = 0
    var fenceChar = 0.toChar()

    var i = 0
    while (i < line.length) {
        val ch = line[i]
        if (!fenceFound) {
            when (ch) {
                ' ' -> if (i > 2) return null
                '`', '~' -> {
                    fenceFound = true
                    fenceChar = ch
                    fenceLen += 1
                }

                else -> return null
            }
        } else {
            if (!infoString && ch == fenceChar) {
                fenceLen += 1
                i++
                continue
            }
            if (fenceLen < 3) return null
            infoString = true
            if ((!opening && ch != ' ' && ch != '\t') || (opening && fenceChar == '`' && ch == '`')) {
                return null
            }
        }
        i++
    }

    return if (fenceLen >= 3) FenceInfo(fenceChar, fenceLen) else null
}
