package org.jetbrains.completion.full.line.python.formatters

import org.junit.jupiter.api.assertAll

class PythonFilesFormatterTest : PythonCodeFormatterTest() {
  private val folder = "test-files"

  fun `test data files`() {
    listOf("data1.py", "data2.py", "data3.py")
      .map { { testFile("$folder/$it") } }
      .let { assertAll("Testing formatting file", *it.toTypedArray()) }
  }
}
