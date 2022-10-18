package org.jetbrains.completion.full.line.java.features

import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.junit.jupiter.api.Assertions

abstract class JavaSuffixTests : FullLineCompletionTestCase() {
  override fun getBasePath() = "testData/completion/features/suffix"

  protected fun doTestJavaQuote(context: String, raw: String, expected: String = raw) {
    Assertions.assertTrue(context.contains("<caret>")) {
      "Context must contains `<caret>` for correct usage"
    }

    val fileType = fileTypeFromLanguage("JAVA")

    myFixture.configureByText(
      fileType,
      """
                public class Main {
                    public static void main(String[] args) {
                        $context
                    }
                }
            """.trimIndent()
    )
    clearFLCompletionCache()
    myFixture.lookup?.hideLookup(true)

    myFixture.completeFullLine(raw)
    Assertions.assertNotNull(myFixture.lookup)
    myFixture.lookupElements!!.filterIsInstance<FullLineLookupElement>().first {
      it.proposal.score == 1.0
    }.let {
      Assertions.assertEquals(expected, it.lookupString + it.suffix.takeWhile { it != '\t' })
    }
  }
}

class CaretSuffixTests : JavaSuffixTests() {
  fun `test CaretPositionWithSuffix`() = doEnterTest("JAVA", "new int[")

  fun `test CaretPositionWithSuffixAndValue`() = doEnterTest("JAVA", "new int[0")
}

class QuoteTests : JavaSuffixTests() {
  fun `test multiple strings`() {
    doTestJavaQuote(
      "<caret>",
      "\"one\" + \"two",
      "\"one\" + \"two\""
    )
    doTestJavaQuote(
      "<caret>",
      "\"one\" + \"two\""
    )
  }

  fun `test multiple single-quoted strings`() {
    doTestJavaQuote(
      "<caret>",
      "'one' + 'two",
      "'one' + 'two'"
    )
    doTestJavaQuote(
      "<caret>",
      "'one' + 'two'"
    )
  }

  fun `test inside string`() {
    doTestJavaQuote(
      "\"test <caret>. context\"",
      "test line with quote \""
    )
    doTestJavaQuote(
      "\".test <caret>. context\"",
      ".test simple line"
    )
  }

  fun `test suffix with less sign`() = doTestJavaQuote("<caret>", "1 < 2")

  fun `test suffix with more sign`() = doTestJavaQuote("<caret>", "2 > 1")

  fun `test suffix with pars`() = doTestJavaQuote(
    "<caret>",
    "(d[f{test", // TODO: bring back triangle brackets after correct fix with them `"(d[f{<test"` => `"(d[f{<test>}])"`
    "(d[f{test}])"
  )

  fun `test suffix with pars and quotes`() = doTestJavaQuote(
    "<caret>",
    "(d[f{\"test", // TODO: bring back triangle brackets after correct fix with them `"(d[f{<\"test"` => `"(d[f{<\"test\">}])"`
    "(d[f{\"test\"}])"
  )

  fun `test unclosed quotes`() = doTestJavaQuote(
    "<caret>",
    "\"test",
    "\"test\""
  )

  fun `test closed quotes`() = doTestJavaQuote(
    "<caret>",
    "\"test\""
  )

  fun `test before string`() = doTestJavaQuote(
    "<caret>\"test\"",
    "\"string",
    "\"string\""
  )

  fun `test after string`() = doTestJavaQuote(
    "\"test\"<caret>",
    "\"string",
    "\"string\""
  )

  fun `test before unclosed string`() = doTestJavaQuote(
    "<caret>\"test",
    "\"string",
    "\"string\""
  )

  fun `test simple`() = doTestJavaQuote(
    "<caret>",
    "simple completion"
  )
}
