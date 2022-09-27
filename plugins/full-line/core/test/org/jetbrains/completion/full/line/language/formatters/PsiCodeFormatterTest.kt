package org.jetbrains.completion.full.line.language.formatters

import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.OffsetsInFile
import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.completion.full.line.FilesTest
import org.jetbrains.completion.full.line.language.PsiCodeFormatter

abstract class PsiCodeFormatterTest(private val formatter: PsiCodeFormatter, private val fileType: FileType) : BasePlatformTestCase() {
  protected fun testFile(originFilename: String, expectedRollbackPrefix: List<String>, lang: String) {
    val origin = FilesTest.readFile("${FilesTest.FORMAT_BEFORE_FOLDER}/$originFilename", lang)
    val newFilename = "${originFilename.substringBeforeLast('.')}.json"
    val formatted = FilesTest.readFile("${FilesTest.FORMAT_AFTER_FOLDER}/$newFilename", lang)

    testCode(origin, formatted, expectedRollbackPrefix)
  }

  private fun testCode(actualCode: String, expectedJson: String, expectedRollbackPrefix: List<String>) {
    val originalFile = myFixture.configureByText(fileType, actualCode)

    val psiFile = OffsetsInFile(originalFile).copyWithReplacement(
      myFixture.caretOffset,
      myFixture.caretOffset,
      CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
    ).file
    val offset = myFixture.caretOffset
    val position = psiFile.findElementAt(offset)!!
    val formatResult = formatter.format(psiFile, position, offset)
    assertEquals(expectedJson.trimEnd(), formatResult.context)
    assertEquals(expectedRollbackPrefix, formatResult.rollbackPrefix)
  }
}
