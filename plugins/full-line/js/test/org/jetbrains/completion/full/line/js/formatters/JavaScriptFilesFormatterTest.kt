package org.jetbrains.completion.full.line.js.formatters

import org.junit.jupiter.api.assertAll

class JavaScriptFilesFormatterTest : JavaScriptCodeFormatterTest() {
  private val folder = "test-files"

  fun `test data files`() {
    listOf("data1.js", "data2.js", "data3.js")
      .map { { testFile("$folder/$it") } }
      .let { assertAll("Testing formatting file", *it.toTypedArray()) }
  }
}
