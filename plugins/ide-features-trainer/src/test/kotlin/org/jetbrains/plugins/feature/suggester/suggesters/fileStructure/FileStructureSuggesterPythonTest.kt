package org.jetbrains.plugins.feature.suggester.suggesters.fileStructure

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.FileStructureSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.FileStructureSuggester.Companion.SUGGESTING_ACTION_ID

class FileStructureSuggesterPythonTest : FileStructureSuggesterTest() {

    override val testingCodeFileName: String = "PythonCodeExample.py"

    override fun `testFind field and get suggestion`() {
        val fromOffset = logicalPositionToOffset(16, 0)
        performFindInFileAction("field", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testFind global variable and get suggestion`() {
        val fromOffset = logicalPositionToOffset(0, 0)
        performFindInFileAction("bcd", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testFind method and get suggestion`() {
        val fromOffset = logicalPositionToOffset(0, 0)
        performFindInFileAction("functi", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testFind class and get suggestion`() {
        val fromOffset = logicalPositionToOffset(0, 0)
        performFindInFileAction("clazz", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testFind function parameter and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(0, 0)
        performFindInFileAction("aaa", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind local variable declaration and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(35, 0)
        performFindInFileAction("strin", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind variable usage and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(10, 0)
        performFindInFileAction("aaa", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind method usage and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(14, 0)
        performFindInFileAction("function", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind type usage and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(31, 9)
        performFindInFileAction("Claz", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}