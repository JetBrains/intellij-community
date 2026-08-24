// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.fir.editor

import junit.framework.TestCase
import org.jetbrains.kotlin.idea.editor.findDollarsToEscapeOnPaste
import org.junit.Assert

/**
 * @see org.jetbrains.kotlin.idea.editor.findDollarsToEscapeOnPaste
 */
class PasteDollarEscapingTest : TestCase() {
    private fun doTest(
        chunkText: String,
        expected: String,
        dollarsBefore: Int = 0,
        dollarsAfter: Int = 0,
        charAfter: Char? = null,
        interpolationPrefixLength: Int = 0,
    ) {
        val dollarsToEscape = findDollarsToEscapeOnPaste(
            chunkText = chunkText,
            dollarsBefore = dollarsBefore,
            dollarsAfter = dollarsAfter,
            charAfter = charAfter,
            interpolationPrefixLength = interpolationPrefixLength,
        )
        val escaped = buildString {
            var from = 0
            for (dollarIndex in dollarsToEscape) {
                append(chunkText, from, dollarIndex)
                append("""\$""")
                from = dollarIndex + 1
            }
            append(chunkText, from, chunkText.length)
        }
        Assert.assertEquals("Unexpected escaping of \"$chunkText\": ", expected, escaped)
    }

    fun `test dollar before digits`() {
        doTest($$"$5.40", $$"$5.40")
    }

    fun `test dollar before punctuation`() {
        doTest("100$, 200$. 50$-60$+", "100$, 200$. 50$-60$+")
    }

    fun `test dollar before whitespace`() {
        doTest("$ x", "$ x")
    }

    fun `test dollar before quote`() {
        doTest("""$"x"""", """$"x"""")
    }

    fun `test dollar before another dollar`() {
        doTest($$"$$5", $$"$$5")
    }

    fun `test dollar at the end of the chunk with nothing after it`() {
        doTest("abc$", "abc$")
    }

    fun `test dollar at the end of the chunk before a closing quote`() {
        doTest("abc$", "abc$", charAfter = '"')
    }

    fun `test dollar at the end of the chunk before a digit`() {
        doTest("abc$", "abc$", charAfter = '4')
    }

    fun `test text without dollars before an identifier`() {
        doTest("abc", "abc", charAfter = 'f')
    }

    fun `test dollar before identifier`() {
        doTest($$"$foo", $$"""\$foo""")
    }

    fun `test dollar before keyword`() {
        doTest($$"$if end", $$"""\$if end""")
    }

    fun `test dollar before block start`() {
        doTest($$"cost: ${", $$"""cost: \${""")
    }

    fun `test dollar before backtick`() {
        doTest($$"$`a", $$"""\$`a""")
    }

    fun `test dollar before underscore`() {
        doTest($$"$_x", $$"""\$_x""")
    }

    fun `test only the last dollar of a sequence is escaped`() {
        doTest($$$"$$foo", $$$"""$\$foo""")
    }

    fun `test several dollar sequences in one chunk`() {
        doTest($$"a$1b$foo$-$c", $$"""a$1b\$foo$-\$c""")
    }

    fun `test dollar at the end of the chunk before an identifier`() {
        doTest("abc$", """abc\$""", charAfter = 'f')
    }

    fun `test dollar at the end of the chunk before a block start`() {
        doTest("abc$", """abc\$""", charAfter = '{')
    }

    fun `test dollar at the end of the chunk before a backtick`() {
        doTest("abc$", """abc\$""", charAfter = '`')
    }

    fun `test single dollar before identifier in a prefixed string`() {
        doTest($$"$foo", $$"$foo", interpolationPrefixLength = 2)
    }

    fun `test two dollars before identifier in a prefixed string`() {
        doTest($$$"$$foo", $$$"""$\$foo""", interpolationPrefixLength = 2)
    }

    fun `test three dollars before identifier in a string prefixed with four dollars`() {
        doTest($$$$"$$$foo", $$$$"$$$foo", interpolationPrefixLength = 4)
    }

    fun `test single trailing dollar before identifier in a prefixed string`() {
        doTest("abc$", "abc$", charAfter = 'f', interpolationPrefixLength = 2)
    }

    fun `test two trailing dollars before identifier in a prefixed string`() {
        doTest("abc$$", """abc$\$""", charAfter = 'f', interpolationPrefixLength = 2)
    }

    fun `test leading dollar completing a sequence with the document`() {
        doTest($$"$foo", $$"""\$foo""", dollarsBefore = 1, interpolationPrefixLength = 2)
    }

    fun `test leading dollar of a non-first sequence ignores the document`() {
        doTest($$"a$foo", $$"a$foo", dollarsBefore = 1, interpolationPrefixLength = 2)
    }

    fun `test trailing dollar completing a sequence with the document`() {
        doTest("abc$", """abc\$""", dollarsAfter = 1, charAfter = 'f', interpolationPrefixLength = 2)
    }

    fun `test trailing dollar when the document already starts an entry`() {
        doTest("abc$", "abc$", dollarsAfter = 1, charAfter = 'f')
    }

    fun `test trailing dollars when the document already starts an entry in a prefixed string`() {
        doTest("abc$$", "abc$$", dollarsAfter = 2, charAfter = 'f', interpolationPrefixLength = 2)
    }

    fun `test dollars before after and pasted together form a prefix`() {
        doTest("$$", """$\$""", dollarsBefore = 1, dollarsAfter = 1, charAfter = 'f', interpolationPrefixLength = 4)
    }
}
