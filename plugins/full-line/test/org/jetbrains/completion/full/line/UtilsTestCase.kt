package org.jetbrains.completion.full.line

import com.intellij.codeInsight.lookup.Lookup
import junit.framework.TestCase
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase

class UtilsTestCase : FullLineCompletionTestCase() {
  fun `test getTestName fun - one test in a != ds`() {
    TestCase.assertEquals("GetTestNameFun-OneTestInA!=Ds", getTestName(false))
  }

  companion object {
    fun Lookup.prefix(): String {
      val text = editor.document.text
      val offset = editor.caretModel.offset
      var startOffset = offset
      for (i in offset - 1 downTo 0) {
        if (!text[i].isJavaIdentifierPart()) break
        startOffset = i
      }
      return text.substring(startOffset, offset)
    }
  }
}
