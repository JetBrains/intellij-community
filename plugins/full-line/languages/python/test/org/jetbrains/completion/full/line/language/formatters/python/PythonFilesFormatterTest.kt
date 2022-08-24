package org.jetbrains.completion.full.line.language.formatters.python

import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class PythonFilesFormatterTest : PythonCodeFormatterTest() {
  private val folder = "test-files"

  fun `test data files`() {
    listOf("data1.py", "data2.py", "data3.py")
      .map { { testFile("$folder/$it") } }
      .let { assertAll("Testing formatting file", *it.toTypedArray()) }
  }
}
