// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import ai.grazie.nlp.utils.takeNonWhitespaces
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContent.Exclusion
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

private val anyTag = Pattern.compile("</?\\w+[^>]*>")
private val closingTag = Pattern.compile("</\\w+\\s*>")

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
    val openingIndex = exclusions.indexOfLast { openingTagName(it.start, it.end) == tagName && content[it.end - 2] != '/' }
    if (openingIndex >= 0) {
      exclusions[openingIndex] = Exclusion.markUnknown(TextRange(exclusions[openingIndex].start, exclusions.last().end))
      exclusions.subList(openingIndex + 1, exclusions.size).clear()
    }
  }

  for (tagRange in Text.allOccurrences(anyTag, content)) {
    ProgressManager.checkCanceled()
    if (closingTag.matcher(content.subSequence(tagRange.startOffset, tagRange.endOffset)).matches()) {
      exclusions.add(Exclusion.markUnknown(tagRange))
      tagClosed(content.substring(tagRange.startOffset + 2, tagRange.endOffset - 1).trim())
    } else if (openingTagName(tagRange.startOffset, tagRange.endOffset) != null) {
      exclusions.add(Exclusion.markUnknown(tagRange))
    }
  }
  return content.excludeRanges(exclusions)
}
