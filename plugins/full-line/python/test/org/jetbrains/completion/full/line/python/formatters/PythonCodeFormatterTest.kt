package org.jetbrains.completion.full.line.python.formatters

import com.intellij.openapi.util.TextRange
import com.jetbrains.python.PythonFileType
import org.jetbrains.completion.full.line.FilesTest
import org.jetbrains.completion.full.line.FilesTest.readFile
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterTest
import org.jetbrains.completion.full.line.python.formatters.PythonCodeFormatter

abstract class PythonCodeFormatterTest : CodeFormatterTest(PythonCodeFormatter()) {

  protected fun testFile(originFilename: String, formattedFilename: String? = null) {
    if (formattedFilename == null) {
      val origin = readFile("${FilesTest.FORMAT_BEFORE_FOLDER}/$originFilename", "python")
      val formatted = readFile("${FilesTest.FORMAT_AFTER_FOLDER}/$originFilename", "python").replace(Regex("[\\n]+"), "\n")

      testCodeFragment(origin, formatted)
    }
    else {
      val origin = readFile(originFilename, "python")
      val formatted = readFile(formattedFilename, "python").replace(Regex("[\\n]+"), "\n")

      testCodeFragment(origin, formatted)
    }
  }

  protected fun testCodeFragment(actualCode: String, expectedCode: String, offset: Int? = null) {
    if (offset != null) {
      assert(actualCode.length >= offset)
    }
    val file = myFixture.configureByText(PythonFileType.INSTANCE, actualCode)
    val ideaContent = formatter.format(file, TextRange(0, offset ?: file.textLength), myFixture.editor)

    assertEquals(expectedCode, ideaContent)
  }
}
