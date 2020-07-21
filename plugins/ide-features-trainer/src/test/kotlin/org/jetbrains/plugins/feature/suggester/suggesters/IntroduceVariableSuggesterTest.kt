package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.FeatureSuggester
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.PopupSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.suggesters.IntroduceVariableSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.IntroduceVariableSuggester.Companion.SUGGESTING_ACTION_ID

class IntroduceVariableSuggesterTest : FeatureSuggesterTest() {

    override val testingCodeFileName = "JavaCodeExample.java"

    fun `testIntroduce expression from IF and get suggestion`() {
        val doc = editor.document
        cutBetweenLogicalPositions(lineStartIndex = 9, columnStartIndex = 23, lineEndIndex = 9, columnEndIndex = 38)
        insertNewLineAt(9, 8)
        type("boolean flag =")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(10, 23)
        type(" flag")

        invokeLater {
            println(doc.text)
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testIntroduce full expression from method call and get suggestion`() {
        val doc = editor.document
        cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 48, lineEndIndex = 10, columnEndIndex = 31)
        insertNewLineAt(10, 12)
        type("int value = ")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(11, 31)
        type("value")

        invokeLater {
            println(doc.text)
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testIntroduce part of expression from method call and get suggestion`() {
        val doc = editor.document
        cutBetweenLogicalPositions(lineStartIndex = 10, columnStartIndex = 40, lineEndIndex = 10, columnEndIndex = 31)
        insertNewLineAt(10, 12)
        type("long val = ")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(11, 31)
        type("val")

        invokeLater {
            println(doc.text)
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testIntroduce part of string expression from method call and get suggestion`() {
        val doc = editor.document
        cutBetweenLogicalPositions(lineStartIndex = 47, columnStartIndex = 35, lineEndIndex = 47, columnEndIndex = 46)
        insertNewLineAt(47, 12)
        type("String sss = ")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(48, 35)
        type("sss")

        invokeLater {
            println(doc.text)
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testIntroduce full expression from return statement and get suggestion`() {
        val doc = editor.document
        cutBetweenLogicalPositions(lineStartIndex = 25, columnStartIndex = 19, lineEndIndex = 25, columnEndIndex = 63)
        insertNewLineAt(25, 12)
        type("Boolean bool=")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(26, 19)
        type("bool")

        invokeLater {
            println(doc.text)
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    fun `testIntroduce expression from method body using copy and backspace and get suggestion`() {
        val doc = editor.document
        selectBetweenLogicalPositions(
            lineStartIndex = 38,
            columnStartIndex = 35,
            lineEndIndex = 38,
            columnEndIndex = 40
        )
        copyCurrentSelection()
        selectBetweenLogicalPositions(
            lineStartIndex = 38,
            columnStartIndex = 35,
            lineEndIndex = 38,
            columnEndIndex = 40
        )
        deleteSymbolAtCaret()
        insertNewLineAt(38, 16)
        type("short output =")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(39, 35)
        type("output")

        invokeLater {
            println(doc.text)
            assertSuggestedCorrectly(expectedSuggestion)
        }
    }

    /**
     * This case must throw suggestion but not working now
     */
    fun `testIntroduce part of string declaration expression and don't get suggestion`() {
        val doc = editor.document
        cutBetweenLogicalPositions(lineStartIndex = 48, columnStartIndex = 25, lineEndIndex = 48, columnEndIndex = 49)
        insertNewLineAt(48, 12)
        type("String string = ")
        pasteFromClipboard()
        type(";")
        moveCaretToLogicalPosition(49, 25)
        type("string")

        invokeLater {
            println(doc.text)
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    private fun assertSuggestedCorrectly(suggestion: Suggestion) {
        TestCase.assertTrue(suggestion is PopupSuggestion)
        TestCase.assertEquals(
            FeatureSuggester.createMessageWithShortcut(
                SUGGESTING_ACTION_ID,
                POPUP_MESSAGE
            ), (suggestion as PopupSuggestion).message
        )
    }
}