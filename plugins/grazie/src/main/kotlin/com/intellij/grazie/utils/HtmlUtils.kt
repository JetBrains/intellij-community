// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.grazie.utils

import com.intellij.grazie.text.TextContent
import com.intellij.openapi.util.TextRange
import kotlinx.html.*
import kotlinx.html.stream.createHTML
import java.util.regex.Matcher
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
private val closingTag = Pattern.compile("</\\w+>")

fun removeHtml(_content: TextContent?): TextContent? {
  var content: TextContent = _content ?: return null

  while (content.startsWith("<html>") || content.startsWith("<body>")) {
    content = content.excludeRange(TextRange(0, 6))
  }
  while (content.endsWith("</html>") || content.endsWith("</body>")) {
    content = content.excludeRange(TextRange.from(content.length - 7, 7))
  }

  while (true) {
    val matcher = closingTag.matcher(content)
    if (!matcher.find()) break

    val text = content.toString()
    val tagName = text.substring(matcher.start() + 2, matcher.end() - 1)
    val openingTag = text.lastIndexOf("<$tagName", matcher.start())
    content = content.markUnknown(TextRange(if (openingTag < 0) matcher.start() else openingTag, matcher.end()))
  }

  while (true) {
    val matcher: Matcher = anyTag.matcher(content)
    if (!matcher.find()) break

    content = content.markUnknown(TextRange(matcher.start(), matcher.end()))
  }
  return content
}
