package org.jetbrains.completion.full.line.features

import com.intellij.codeInsight.lookup.Lookup
import org.jetbrains.completion.full.line.platform.tests.FullLineCompletionTestCase
import org.jetbrains.completion.full.line.platform.tests.PythonProject

class PrefixTests : FullLineCompletionTestCase(), PythonProject {
    private fun pyDocContext(content: String) = """
        def function_with_doc_string(function):
            ""\"$content""\"
    """.trimIndent()

    fun `test in start`() = doPyDocTest("<caret> word", "")

    fun `test in end`() = doPyDocTest("word <caret>", "")

    fun `test after word`() = doPyDocTest("word<caret> is test", "word")

    fun `test after multiple words`() = doPyDocTest("one two three word<caret> is test", "word")

    fun `test inside word`() = doPyDocTest("my super<caret>long word", "super")

    private fun doPyDocTest(content: String, expectedPrefix: String) {
        myFixture.configureByText("test.py", pyDocContext(content))
        clearFLCompletionCache()
        myFixture.completeFullLine("$expectedPrefix-test-var")

        assertNotNull(myFixture.lookup)
        val prefix = myFixture.lookup.prefix()
        assertEquals(expectedPrefix, prefix)
    }
}

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
