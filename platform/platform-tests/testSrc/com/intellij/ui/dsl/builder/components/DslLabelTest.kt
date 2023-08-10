// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.dsl.builder.components

import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.ui.dsl.UiDslException
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import org.junit.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class DslLabelTest {

  @Test
  fun testSetHtmlText() {
    val externalLink = DocumentationMarkup.EXTERNAL_LINK_ICON.toString()
      .replace("/>", ">") // End of the tag removed by html framework
    val testValues = mapOf(
      """Some text""" to """Some text""",
      """Some <a>link</a>""" to """Some <a href="">link</a>""",
      """Some <a href="aaa">link</a>""" to """Some <a href="aaa">link</a>""",
      """Some text<a href="http://url">a link</a>""" to """Some text<a href="http://url">a link$externalLink</a>""",
      """Some text<a href="https://url">a link</a>""" to """Some text<a href="https://url">a link$externalLink</a>""",
      """Some text<a href="nothttps://url">a link</a>""" to """Some text<a href="nothttps://url">a link</a>""",
      """<a href="https://url">https</a>, <a>link</a>, <a href="http://url">http</a>""" to
        """<a href="https://url">https$externalLink</a>, <a href="">link</a>, <a href="http://url">http$externalLink</a>""",
    )
    val bodyRegex = Regex("<body>(.*)</body>", RegexOption.DOT_MATCHES_ALL)
    val newLineRegex = Regex("\\s+", RegexOption.DOT_MATCHES_ALL)
    val dslLabel = DslLabel(DslLabelType.LABEL)

    fun getDslLabelBody(text: String): String {
      dslLabel.maxLineLength = MAX_LINE_LENGTH_NO_WRAP
      dslLabel.text = text
      return bodyRegex.find(dslLabel.text)!!.groups[1]!!
        .value.trim()
        .replace(newLineRegex, " ") // Remove new lines with indents
    }

    for ((text, expectedResult) in testValues) {
      assertEquals(expectedResult, getDslLabelBody(text))
      assertEquals(expectedResult, getDslLabelBody(text.replace('\"', '\'')))
    }
  }

  @Test
  fun testInvalidHtmlText() {
    val testValues = listOf(
      "<html>text",
      "<BODY>text",
      "<a href=''>text</a>",
      """<a  HREF = "" >""",
    )

    val dslLabel = DslLabel(DslLabelType.LABEL)
    for (text in testValues) {
      assertThrows<UiDslException>(text) {
        dslLabel.maxLineLength = MAX_LINE_LENGTH_NO_WRAP
        dslLabel.text = text
      }
    }
  }
}
