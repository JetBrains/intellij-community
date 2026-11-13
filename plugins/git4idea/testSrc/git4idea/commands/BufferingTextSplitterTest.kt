// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BufferingTextSplitterTest {
  @Test
  fun `test line split with LF`() {
    check(listOf("line1\nline2\n"),
          listOf("line1", "line2"))
  }

  @Test
  fun `test line split with CR`() {
    check(listOf("line1\rline2\r"),
          listOf("line1!", "line2!"))
  }

  @Test
  fun `test line split with CRLF`() {
    check(listOf("line1\r\nline2\r\n"),
          listOf("line1", "line2"))
  }

  @Test
  fun `test line split with a mix`() {
    check(listOf("lineLF\nlineCR\rlineCRLF\r\nlineLFCR\n\r"),
          listOf("lineLF", "lineCR!", "lineCRLF", "lineLFCR", "!"))
  }

  @Test
  fun `test flush`() {
    check(listOf("lineLF\nbad line"),
          listOf("lineLF", "bad line"))
  }

  @Test
  fun `test lines glued`() {
    check(listOf("start", "end\n"),
          listOf("startend"))
  }

  @Test
  fun `test lines not glued`() {
    check(listOf("lineCR\r", "lineLF\n", "lineCRLF\r\n", "lineLFCR\n\r"),
          listOf("lineCR!", "lineLF", "lineCRLF", "lineLFCR", "!"))
  }

  @Test
  fun `test lines split separate CR LF`() {
    check(listOf("lineCR\r", "\nlineLF"),
          listOf("lineCR", "lineLF"))

    check(listOf("lineCR\r", "\rlineLF"),
          listOf("lineCR!", "!", "lineLF"))

    check(listOf("lineLF\n", "lineLF"),
          listOf("lineLF", "lineLF"))

    check(listOf("lineCR\r", "lineLF"),
          listOf("lineCR!", "lineLF"))

    check(listOf("lineLF", "\nlineLF"),
          listOf("lineLF", "lineLF"))

    check(listOf("lineCR", "\rlineLF"),
          listOf("lineCR!", "lineLF"))

    check(listOf("lineLF", "\r\nlineLF"),
          listOf("lineLF", "lineLF"))
  }

  @Test
  fun `test empty lines`() {
    check(listOf("\r\r\r"),
          listOf("!", "!", "!"))

    check(listOf("\n\n\n"),
          listOf("", "", ""))

    check(listOf("\r\n\r\n\r\n"),
          listOf("", "", ""))

    check(listOf("\r\r\n\n\n"),
          listOf("!", "", "", ""))
  }

  private fun check(text: List<String>, expectedLines: List<String>) {
    val result = mutableListOf<String>()
    val outputSplitter = BufferingTextSplitter { line, isCr -> result.add(line + if (isCr) "!" else "") }
    text.forEach { outputSplitter.process(it.toCharArray(), it.length) }
    outputSplitter.flush()
    assertEquals(expectedLines, result)
  }
}