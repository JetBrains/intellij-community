package org.jetbrains.completion.full.line.features

import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.fileTypes.FileTypeManager
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.junit.jupiter.api.Assertions

abstract class SuffixTests : FullLineCompletionTestCase() {
    override fun getBasePath() = "testData/completion/features/suffix"

    protected fun doTestJavaQuote(context: String, raw: String, expected: String = raw) {
        Assertions.assertTrue(context.contains("<caret>")) {
            "Context must contains `<caret>` for correct usage"
        }

        val lang = Language.findInstance(JavaLanguage::class.java)
        val fileType = FileTypeManager.getInstance().findFileTypeByLanguage(lang)

        myFixture.configureByText(
            fileType!!,
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

class CaretSuffixTests : SuffixTests() {
    fun `test CaretPositionWithSuffix`() = doEnterTest("JAVA", "new int[")

    fun `test CaretPositionWithSuffixAndValue`() = doEnterTest("JAVA", "new int[0")

    fun `test suffix with override chars`() = doEnterTest("Python", "map(int, input().split(\"")

    fun `test suffix with override suffix`() = doEnterTest("Python", "map(int, input().split")

    // https://jetbrains.slack.com/archives/GKW1WVAV7/p1644331514433269
    fun `test in string char suffix`() = doEnterTest("Python", "{color}.txt\", \"r\") as f:")

    // https://jetbrains.slack.com/archives/GKW1WVAV7/p1644331584258539
    fun `test suffix after multiple strings`() = doEnterTest("Python", "t\", \"--with_target\", action=\"store_true")
}

class QuoteTests : SuffixTests() {
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
