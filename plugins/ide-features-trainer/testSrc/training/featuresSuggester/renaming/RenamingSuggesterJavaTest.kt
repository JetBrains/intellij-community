package training.featuresSuggester.renaming

import junit.framework.TestCase
import org.junit.Ignore
import training.featuresSuggester.NoSuggestion

@Ignore
class RenamingSuggesterJavaTest : RenamingSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"

    override fun `testAdd one symbol to identifiers of local variable and catch suggestion`() {
        moveCaretToLogicalPosition(6, 15)
        myFixture.type("1")
        moveCaretToLogicalPosition(9, 14)
        myFixture.type("1")
        moveCaretToLogicalPosition(10, 34)
        myFixture.type("1")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testRemove one symbol from identifiers of local variable and catch suggestion`() {
        moveCaretToLogicalPosition(6, 14)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(9, 13)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(10, 33)
        deleteSymbolAtCaret()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of local variable using different ways of typing and removing characters and catch suggestion`() {
        moveCaretToLogicalPosition(6, 15)
        deleteSymbolAtCaret()
        myFixture.type("1")
        deleteSymbolAtCaret()
        myFixture.type("de")

        moveCaretToLogicalPosition(9, 14)
        myFixture.type("1")
        deleteSymbolsAtCaret(2)
        myFixture.type("dec")
        deleteSymbolAtCaret()

        moveCaretToLogicalPosition(10, 34)
        deleteSymbolsAtCaret(2)
        myFixture.type("bde")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit one identifier of local variable, replace old identifiers with edited identifier (using Copy+Paste) and catch suggestion`() {
        moveCaretToLogicalPosition(6, 15)
        myFixture.type("1")
        moveCaretRelatively(-4, 0, true)
        copyCurrentSelection()

        moveCaretToLogicalPosition(9, 14)
        moveCaretRelatively(-3, 0, true)
        pasteFromClipboard()

        moveCaretToLogicalPosition(10, 34)
        moveCaretRelatively(-3, 0, true)
        pasteFromClipboard()

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of method and catch suggestion`() {
        moveCaretToLogicalPosition(15, 30)
        myFixture.type("nyFun")
        moveCaretToLogicalPosition(9, 27)
        myFixture.type("nyFun")
        moveCaretToLogicalPosition(8, 11)
        myFixture.type("nyFun")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of field and catch suggestion`() {
        moveCaretToLogicalPosition(10, 43)
        myFixture.type("aa")
        moveCaretToLogicalPosition(12, 33)
        myFixture.type("aa")
        moveCaretToLogicalPosition(2, 33)
        myFixture.type("aa")
        moveCaretToLogicalPosition(7, 22)
        myFixture.type("aa")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of function parameter and catch suggestion`() {
        moveCaretToLogicalPosition(25, 62)
        myFixture.type("ument")
        moveCaretToLogicalPosition(25, 22)
        myFixture.type("ument")
        moveCaretToLogicalPosition(24, 51)
        myFixture.type("ument")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testEdit identifiers of field but leave them unchanged and don't catch suggestion`() {
        moveCaretToLogicalPosition(6, 15)
        deleteSymbolsAtCaret(2)
        myFixture.type("bc")

        moveCaretToLogicalPosition(9, 14)
        deleteSymbolsAtCaret(2)
        myFixture.type("bc")

        moveCaretToLogicalPosition(10, 34)
        deleteSymbolsAtCaret(2)
        myFixture.type("bc")

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testEdit identifiers that references to different variables and don't catch suggestion`() {
        moveCaretToLogicalPosition(28, 39)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(25, 43)
        deleteSymbolAtCaret()
        moveCaretToLogicalPosition(25, 34)
        deleteSymbolAtCaret()

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
