package org.jetbrains.completion.full.line.python.features

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.impl.LookupImpl
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.jetbrains.completion.full.line.python.tests.FullLinePythonCompletionTestCase
import org.junit.jupiter.api.Assertions

class DefaultCompletionTests : FullLinePythonCompletionTestCase() {
  fun `test selecting default with enter`() = doTestWithElse('\n')
  fun `test selecting default with tag`() = doTestWithElse('\t')
  fun `test selecting default with space`() = doTestWithElse(' ')

  private fun doTestWithElse(completionChar: Char) = doTest(
    """
            if True:
            e<caret> 
        """.trimIndent(),
    completionChar
  )

  //TODO: rewrite with calling postfix templates
  private fun doTest(context: String, completionChar: Char) {
    Assertions.assertTrue(context.contains("<caret>")) {
      "Context must contains `<caret>` for correct usage"
    }

    myFixture.configureByText("temp.py", context)
    myFixture.complete(CompletionType.BASIC)

    val lookup = myFixture.lookup as LookupImpl
    assertNotNull(lookup)
    assertNotEmpty(lookup.items)
    lookup.selectedIndex = lookup.items.indexOfFirst { it.lookupString == "else" }

    myFixture.type(completionChar)

    println(myFixture.file.text)
    assertTrue(myFixture.file.text.trimEnd().endsWith("else:"))
  }
}
