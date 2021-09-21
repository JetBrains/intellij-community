package org.jetbrains.plugins.feature.suggester.suggesters.renaming

import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.junit.Ignore

@Ignore
class RenamingSuggesterPythonTest : RenamingSuggesterTest() {

    override val testingCodeFileName: String = "PythonCodeExample.py"

    override fun `testAdd one symbol to identifiers of local variable and catch suggestion`() {
        moveCaretToLogicalPosition(15, 31)
        myFixture.type("1")
        moveCaretToLogicalPosition(1, 3)
        myFixture.type("1")
        moveCaretToLogicalPosition(3, 12)
        myFixture.type("1")
        moveCaretToLogicalPosition(6, 13)
        myFixture.type("1")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testRemove one symbol from identifiers of local variable and catch suggestion`() {
        moveCaretToLogicalPosition(15, 31)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(1, 3)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(3, 12)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(6, 13)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of local variable using different ways of typing and removing characters and catch suggestion`() {
        moveCaretToLogicalPosition(15, 31)
        deleteSymbolAtCaret()
        myFixture.type("1")
        deleteSymbolAtCaret()
        myFixture.type("de")

        moveCaretToLogicalPosition(1, 3)
        myFixture.type("1")
        deleteSymbolsAtCaret(2)
        myFixture.type("dec")
        deleteSymbolAtCaret()

        moveCaretToLogicalPosition(3, 12)
        deleteSymbolsAtCaret(2)
        myFixture.type("cde")

        moveCaretToLogicalPosition(6, 13)
        myFixture.type("e")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit one identifier of local variable, replace old identifiers with edited identifier (using Copy+Paste) and catch suggestion`() {
        moveCaretToLogicalPosition(15, 31)
        myFixture.type("1")
        moveCaretRelatively(-4, 0, true)
        copyCurrentSelection()

        moveCaretToLogicalPosition(1, 3)
        moveCaretRelatively(-3, 0, true)
        pasteFromClipboard()

        moveCaretToLogicalPosition(3, 12)
        moveCaretRelatively(-3, 0, true)
        pasteFromClipboard()

        moveCaretToLogicalPosition(6, 13)
        moveCaretRelatively(-3, 0, true)
        pasteFromClipboard()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of method and catch suggestion`() {
        moveCaretToLogicalPosition(15, 8)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(9, 12)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(15, 21)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of field and catch suggestion`() {
        moveCaretToLogicalPosition(27, 51)
        myFixture.type("aa")
        moveCaretToLogicalPosition(28, 19)
        myFixture.type("aa")
        moveCaretToLogicalPosition(18, 4)
        myFixture.type("aa")
        moveCaretToLogicalPosition(24, 29)
        myFixture.type("aa")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of function parameter and catch suggestion`() {
        moveCaretToLogicalPosition(10, 16)
        myFixture.type("abc")
        moveCaretToLogicalPosition(9, 21)
        myFixture.type("abc")
        moveCaretToLogicalPosition(13, 17)
        myFixture.type("abc")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of field but leave them unchanged and don't catch suggestion`() {
        moveCaretToLogicalPosition(18, 9)
        deleteSymbolsAtCaret(2)
        myFixture.type("ld")

        moveCaretToLogicalPosition(24, 34)
        deleteSymbolsAtCaret(2)
        myFixture.type("ld")

        moveCaretToLogicalPosition(27, 56)
        deleteSymbolsAtCaret(2)
        myFixture.type("ld")

        moveCaretToLogicalPosition(28, 24)
        deleteSymbolsAtCaret(2)
        myFixture.type("ld")

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testEdit identifiers that references to different variables and don't catch suggestion`() {
        moveCaretToLogicalPosition(9, 21)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(10, 16)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(21, 20)
        deleteSymbolAtCaret()

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
