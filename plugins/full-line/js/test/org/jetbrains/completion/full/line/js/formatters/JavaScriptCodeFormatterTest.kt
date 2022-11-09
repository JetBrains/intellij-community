package org.jetbrains.completion.full.line.js.formatters

import com.intellij.lang.javascript.JavaScriptFileType
import com.intellij.openapi.util.TextRange
import org.jetbrains.completion.full.line.FilesTest
import org.jetbrains.completion.full.line.FilesTest.readFile
import org.jetbrains.completion.full.line.language.formatters.CodeFormatterTest

abstract class JavaScriptCodeFormatterTest : CodeFormatterTest(JSCodeFormatter()) {

  protected fun testFile(originFilename: String, formattedFilename: String? = null) {
    if (formattedFilename == null) {
      val origin = readFile("${FilesTest.FORMAT_BEFORE_FOLDER}/$originFilename", "js")
      val formatted = readFile("${FilesTest.FORMAT_AFTER_FOLDER}/$originFilename", "js")

      testCodeFragment(origin, formatted)
    }
    else {
      val origin = readFile(originFilename, "js")
      val formatted = readFile(formattedFilename, "js")

      testCodeFragment(origin, formatted)
    }
  }

  protected fun testCodeFragment(actualCode: String, expectedCode: String, offset: Int? = null) {
    if (offset != null) {
      assert(actualCode.length >= offset)
    }
    val file = myFixture.configureByText(JavaScriptFileType.INSTANCE, actualCode)
    val ideaContent = formatter.format(file, TextRange(0, offset ?: file.textLength), myFixture.editor)

    assertEquals(expectedCode, ideaContent)
  }
}
