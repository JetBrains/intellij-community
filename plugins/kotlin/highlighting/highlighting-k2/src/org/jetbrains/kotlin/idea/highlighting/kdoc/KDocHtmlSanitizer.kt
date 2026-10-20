// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.highlighting.kdoc

import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.kotlin.idea.highlighting.kdoc.KDocHtmlSanitizer.ACCEPTABLE_TAGS
import org.jetbrains.kotlin.idea.highlighting.kdoc.KDocHtmlSanitizer.TAG_REPLACEMENTS

/**
 * Sanitizes the raw HTML in a KDoc comment for Quick Documentation.
 *
 * The tags are mostly copied from `com.intellij.markdown.utils.doc.DocMarkdownToHtmlConverter`, with some additional ones.
 *
 * ***Important:*** The aim is to escape unsupported tags that cannot be rendered rather than preventing XSS in all cases.
 * The HTML doc renderer used does not have a JavaScript engine, so traditional XSS is not possible.
 * Do not use this sanitizer for emitting the HTML to contexts where JavaScript might be enabled.
 */
internal object KDocHtmlSanitizer {
    /**
     * Tags that are not allowed as is in HTML but get replaced by more appropriate alternatives before rendering.
     */
    private val TAG_REPLACEMENTS: Map<String, String> = mapOf(
        "div" to "span",
        "em" to "i",
        "strong" to "b",
    )

    /**
     * Tags that are allowed in KDoc HTML.
     * A tag also in [TAG_REPLACEMENTS] needs to be replaced first.
     */
    private val ACCEPTABLE_TAGS: Set<String> = setOf(
        // Text content
        "blockquote", "dd", "dl", "dt", "hr", "li", "ol", "ul", "pre", "p",
        // Table
        "caption", "col", "colgroup", "table", "tbody", "td", "tfoot", "th", "thead", "tr",
        "details", "summary",
        // Content sectioning
        "h1", "h2", "h3", "h4", "h5", "h6",
        // Inline text semantic
        "a", "b", "br", "code", "em", "i", "s", "span", "strong", "u", "wbr", "kbd", "samp", "sub", "sup",
        // Svg and math
        "svg",
        // Obsolete
        "tt",
        // Special IntelliJ tags
        "shortcut", "icon"
    )

    /**
     * The start of an open or closing tag.
     * Group 1 is the `/` for a closing tag. Group 2 is the tag name.
     */
    private val TAG_START_PATTERN = Regex("""<(/?)([A-Za-z][A-Za-z0-9-]*)""")

    /**
     * One attribute of an HTML tag.
     * Note: quoted attribute values can contain `<` and `>`.
     */
    private val ATTRIBUTE_PATTERN = Regex("""\s+[A-Za-z_:][A-Za-z0-9_.:-]*(?:\s*=\s*(?:[^\s"'=<>`]+|'[^']*'|"[^"]*"))?""")

    private val TAG_END_PATTERN = Regex("""\s*/?>""")

    /**
     * Appends the sanitized tag that starts at [start] and returns the offset after it.
     * When no complete tag starts at [start], appends an escaped `<` and returns the next offset.
     */
    private fun StringBuilder.appendSanitizedTag(html: CharSequence, start: Int): Int {
        val tagStart = TAG_START_PATTERN.matchAt(html, start)
        val tagEnd = if (tagStart != null) findTagEnd(html, tagStart) else -1
        if (tagStart == null || tagEnd < 0) {
            append("&lt;")
            return start + 1
        }

        val tagMatch = tagStart.groups[2]!!
        val tagName = tagMatch.value.lowercase()
        val tagReplacement = TAG_REPLACEMENTS[tagName]
        when {
            // For tag replacements we should keep their attributes, so we need to do some extra work
            tagReplacement != null ->
                append(html, start, tagMatch.range.first) // Before the tag name
                    .append(tagReplacement) // new tag
                    .append(html, tagMatch.range.last + 1, tagEnd) // attributes, etc. until the end of the tag

            tagName in ACCEPTABLE_TAGS -> append(html, start, tagEnd) // allow the entire tag unmodified
            else -> append(StringUtil.escapeXmlEntities(html.substring(start, tagEnd))) // do not allow the tag, escape it
        }
        return tagEnd
    }

    /**
     * Returns the offset after the `>` of the tag starting at [tagStart], or -1 if the tag is not complete.
     */
    private fun findTagEnd(html: CharSequence, tagStart: MatchResult): Int {
        var index = tagStart.range.last + 1
        val isClosingTag = tagStart.groupValues[1].isNotEmpty()
        if (!isClosingTag) {
            // Skip attributes inside the tag
            while (true) {
                index = ATTRIBUTE_PATTERN.matchAt(html, index)?.range?.last?.plus(1) ?: break
            }
        }
        // Now we are at the end of the tag and we need to find `/>` or `>`
        val tagClose = TAG_END_PATTERN.matchAt(html, index) ?: return -1
        return tagClose.range.last + 1
    }

    /**
     * Sanitizes each HTML tag in [html].
     * Tags not within [ACCEPTABLE_TAGS] and [TAG_REPLACEMENTS] are escaped.
     * The content of the tags is never affected.
     */
    fun sanitize(html: CharSequence): String {
        val result = StringBuilder(html.length)
        var index = 0
        while (true) {
            val lessThan = html.indexOf('<', index)
            if (lessThan < 0) break
            result.append(html, index, lessThan)
            index = result.appendSanitizedTag(html, lessThan)
        }
        return result.append(html, index, html.length).toString()
    }
}
