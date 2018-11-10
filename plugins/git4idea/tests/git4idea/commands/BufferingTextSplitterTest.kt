// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.commands

import com.intellij.testFramework.UsefulTestCase

class BufferingTextSplitterTest : UsefulTestCase() {
  fun `test line split with LF`() {
    check(listOf("line1\nline2\n"),
          listOf("line1\n", "line2\n"))
  }

  fun `test line split with CR`() {
    check(listOf("line1\rline2\r"),
          listOf("line1\r", "line2\r"))
  }

  fun `test line split with CRLF`() {
    check(listOf("line1\r\nline2\r\n"),
          listOf("line1\r\n", "line2\r\n"))
  }

  fun `test line split with a mix`() {
    check(listOf("lineLF\nlineCR\rlineCRLF\r\nlineLFCR\n\r"),
          listOf("lineLF\n", "lineCR\r", "lineCRLF\r\n", "lineLFCR\n", "\r"))
  }

  fun `test flush`() {
    check(listOf("lineLF\nbad line"),
          listOf("lineLF\n", "bad line"))
  }

  fun `test lines glued`() {
    check(listOf("start", "end\n"),
          listOf("startend\n"))
  }

  fun `test lines not glued`() {
    check(listOf("lineCR\r", "lineLF\n", "lineCRLF\r\n", "lineLFCR\n\r"),
          listOf("lineCR\r", "lineLF\n", "lineCRLF\r\n", "lineLFCR\n", "\r"))
  }

  fun `test lines split separate CR LF`() {
    check(listOf("lineCR\r", "\nlineLF"),
          listOf("lineCR\r\n", "lineLF"))
  }

  fun `test empty lines`() {
    check(listOf("\r\r\r"),
          listOf("\r", "\r", "\r"))

    check(listOf("\n\n\n"),
          listOf("\n", "\n", "\n"))

    check(listOf("\r\n\r\n\r\n"),
          listOf("\r\n", "\r\n", "\r\n"))

    check(listOf("\r\r\n\n\n"),
          listOf("\r", "\r\n", "\n", "\n"))
  }

  private fun check(text: List<String>, expectedLines: List<String>) {
    val result = mutableListOf<String>()
    val outputSplitter = BufferingTextSplitter({ line -> result.add(line) })
    text.forEach { outputSplitter.process(it.toCharArray(), it.length) }
    outputSplitter.flush()
    assertOrderedEquals(result, expectedLines)
  }
}