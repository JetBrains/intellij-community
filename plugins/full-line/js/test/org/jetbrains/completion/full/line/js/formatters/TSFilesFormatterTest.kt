package org.jetbrains.completion.full.line.js.formatters

import org.junit.jupiter.api.assertAll

class TSFilesFormatterTest : JSTSCodeFormatterTest() {
  override var lang = "ts"
  private val folder = "test-files"

  fun `test ts data files`() {
    listOf("data1.ts", "data2.ts", "data3.ts")
      .map { { testFile("$folder/$it") } }
      .let { assertAll("Testing formatting file", *it.toTypedArray()) }
  }
}
