// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import ai.grazie.nlp.utils.takeNonWhitespaces
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.Exclusion
import com.intellij.grazie.text.TextContent.ExclusionKind
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.util.regex.Pattern

fun html(body: BODY.() -> Unit) = createHTML(false).html { body { body(this) } }

var TABLE.cellpading: String
  get() = attributes["cellpadding"] ?: ""
  set(value) {
    attributes["cellpadding"] = value
  }

var TABLE.cellspacing: String
  get() = attributes["cellspacing"] ?: ""
  set(value) {
    attributes["cellspacing"] = value
  }

var TD.valign: String
  get() = attributes["valign"] ?: ""
  set(value) {
    attributes["valign"] = value
  }

fun FlowContent.nbsp() = +Entities.nbsp

private val anyTag = Pattern.compile("</?(\\w+)[^>]*>")
private val closingTag = Pattern.compile("</\\w+\\s*>")

@JvmField
val commonBlockElements: Set<String> =
  setOf("body", "p", "br", "td", "li", "title", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "table", "ol", "ul")

private val commonMarkupElements = setOf("span", "i", "b", "u", "font", "a", "s", "strong", "sub", "sup")

/**
 * Remove HTML markup from a text, splitting it at block elements (like {@code <p>}),
 * marking common HTML markup tags (like {@code <i>}) as markup offsets,
 * and replacing all other tags with unknown fragments.
 */
fun excludeHtml(content: TextContent?): List<TextContent> {
  if (content == null) return emptyList()

  val components = ArrayList<TextContent>()
  var lastComponentStart = 0
  var matchEnd = 0
  val matcher = anyTag.matcher(content)
  while (matcher.find(matchEnd)) {
    matchEnd = matcher.end()
    ProgressManager.checkCanceled()

    val tagName = matcher.group(1)
    if (tagName in commonBlockElements) {
      content.subText(TextRange(lastComponentStart, matcher.start()))?.let(components::add)
      lastComponentStart = matcher.end()
    }
  }
  content.subText(TextRange(lastComponentStart, content.length))?.let(components::add)

  @Suppress("DEPRECATION")
  return components.mapNotNull { removeHtml(it)?.trimWhitespace() }
}

/** Remove HTML markup from a text, replacing it with unknown or markup (for some common HTML tags) offsets. */
@Deprecated("use excludeHtml", ReplaceWith("excludeHtml"))
fun removeHtml(_content: TextContent?): TextContent? {
  var content: TextContent = _content ?: return null

  while (content.startsWith("<html>") || content.startsWith("<body>")) {
    content = content.excludeRange(TextRange(0, 6))
  }
  while (content.endsWith("</html>") || content.endsWith("</body>")) {
    content = content.excludeRange(TextRange.from(content.length - 7, 7))
  }

  val exclusions = arrayListOf<Exclusion>()

  fun openingTagName(tagRangeStart: Int, tagRangeEnd: Int): String? =
    if (Character.isLetter(content[tagRangeStart + 1])) content.substring(tagRangeStart + 1, tagRangeEnd - 1).takeNonWhitespaces()
    else null

  fun tagClosed(tagName: String) {
    if (tagName in commonMarkupElements) return

    val openingIndex = exclusions.indexOfLast { openingTagName(it.start, it.end) == tagName && content[it.end - 2] != '/' }
    if (openingIndex >= 0) {
      exclusions[openingIndex] = Exclusion.markUnknown(TextRange(exclusions[openingIndex].start, exclusions.last().end))
      exclusions.subList(openingIndex + 1, exclusions.size).clear()
    }
  }

  var matchEnd = 0
  val matcher = anyTag.matcher(content)
  while (matcher.find(matchEnd)) {
    matchEnd = matcher.end()
    ProgressManager.checkCanceled()
    val matchStart = matcher.start()
    val tagName = matcher.group(1)
    if (!tagName[0].isLetterOrDigit()) continue

    val exclusionKind = if (tagName in commonMarkupElements) ExclusionKind.markup else ExclusionKind.unknown
    if (closingTag.matcher(content.subSequence(matchStart, matchEnd)).matches()) {
      exclusions.add(Exclusion(matchStart, matchEnd, exclusionKind))
      tagClosed(content.substring(matchStart + 2, matchEnd - 1).trim())
    } else {
      exclusions.add(Exclusion(matchStart, matchEnd, exclusionKind))
    }
  }
  return content.excludeRanges(exclusions)
}

private val nbsp = Pattern.compile("&nbsp;")
private val tab = Pattern.compile("&#9;")

fun isSpaceEntity(text: String): Boolean = text == nbsp.pattern() || text == tab.pattern()

private fun inlineEntity(content: TextContent?, pattern: Pattern, space: Char): TextContent? {
  if (content == null) return null

  val spaces = Text.allOccurrences(pattern, content)
  if (spaces.isEmpty()) return content.trimWhitespace()

  val components = arrayListOf<TextContent?>()
  for (i in spaces.indices) {
    val prevEnd = if (i == 0) 0 else spaces[i - 1].endOffset
    components.add(content.subText(TextRange(prevEnd, spaces[i].startOffset))?.trimWhitespace())
  }
  components.add(content.subText(TextRange(spaces.last().endOffset, content.length))?.trimWhitespace())
  return TextContent.joinWithWhitespace(space, components.filterNotNull())
}

fun inlineSpaceEntities(content: TextContent?): TextContent? {
  return inlineEntity(nbspToSpace(content), tab, '\t')
}

fun nbspToSpace(content: TextContent?): TextContent? {
  return inlineEntity(content, nbsp, ' ')
}

