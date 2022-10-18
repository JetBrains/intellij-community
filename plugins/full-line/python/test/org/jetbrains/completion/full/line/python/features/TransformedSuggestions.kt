package org.jetbrains.completion.full.line.python.features

import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.python.tests.FullLinePythonCompletionTestCase

class TransformedSuggestions : FullLinePythonCompletionTestCase() {
  private val context = "\n<caret>"

  fun `test empty lookup shown`() {
    myFixture.configureByText("fragment.py", context)
    clearFLCompletionCache()
    myFixture.completeBasic()

    assertNotNull(myFixture.lookup)
    assertTrue(myFixture.fullLineElement().isEmpty())
  }

  fun `test not showing BOS`() = doTestNotShowing("BOS", "<BOS", "<BOS>")

  fun `test not showing nonsense`() = doTestNotShowing("-+|", "():", "!", ".", "()")

  fun `test not showing empty`() = doTestNotShowing("   ", "\t\n", "")

  fun `test not showing repetition`() = doTestNotShowing(
    "model.model.model.",
    "varvarvarvar",
    "000",
  )

  fun `test transformed uncompleted var`() = doTestTransform("not completed_one_", "not")

  fun `test transformed uncompleted fun`() = doTestTransform("a = my.func.not_completed_one_", "a = my.func.")

  // Test that check if suggestions are not filtered/transformed

  fun `test showing equations`() = doTestKeepShowing("1 + 2", "1+2", "1, 2", ", 1):")

  fun `test showing correct samples`() = doTestKeepShowing("a", "from i import j", "a = b", "def a(1, 2, 'str'):")

  private fun doTestTransform(initial: String, expected: String) {
    myFixture.configureByText("fragment.py", context)
    clearFLCompletionCache()
    myFixture.completeFullLine(initial)

    assertNotNull(myFixture.lookup)

    val elements = myFixture.lookupElements!!.filterIsInstance<FullLineLookupElement>().map { it.lookupString }

    assertContainsElements(elements, expected)
    assertDoesntContain(elements, initial)
  }

  private fun doTestNotShowing(vararg suggestions: String) = doTestFilter(emptyList(), *suggestions)

  private fun doTestKeepShowing(vararg suggestions: String) = doTestFilter(suggestions.toList(), *suggestions)

  private fun doTestFilter(expected: List<String>, vararg suggestions: String) {
    myFixture.configureByText("fragment.py", context)
    clearFLCompletionCache()
    myFixture.completeFullLine(*suggestions)

    assertNotNull(myFixture.lookup)

    assertEquals(
      expected.sorted(),
      myFixture.lookupElements!!.filterIsInstance<FullLineLookupElement>().map { it.lookupString }.sorted()
    )
  }
}
