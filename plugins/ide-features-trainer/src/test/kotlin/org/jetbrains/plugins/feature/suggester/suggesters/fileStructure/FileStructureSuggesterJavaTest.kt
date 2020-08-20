package org.jetbrains.plugins.feature.suggester.suggesters.fileStructure

import com.intellij.openapi.application.invokeLater
import junit.framework.TestCase
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.suggesters.FileStructureSuggester.Companion.POPUP_MESSAGE
import org.jetbrains.plugins.feature.suggester.suggesters.FileStructureSuggester.Companion.SUGGESTING_ACTION_ID

class FileStructureSuggesterJavaTest : FileStructureSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"

    override fun `testFind field and get suggestion`() {
        val fromOffset = logicalPositionToOffset(1, 0)
        performFindInFileAction("field", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testFind method and get suggestion`() {
        val fromOffset = logicalPositionToOffset(4, 0)
        performFindInFileAction("mai", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    fun `testFind class and get suggestion`() {
        val fromOffset = logicalPositionToOffset(14, 0)
        performFindInFileAction("staticCl", fromOffset)
        focusEditor()

        invokeLater {
            assertSuggestedCorrectly(SUGGESTING_ACTION_ID, POPUP_MESSAGE)
        }
    }

    override fun `testFind function parameter and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(4, 0)
        performFindInFileAction("args", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind local variable declaration and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(4, 0)
        performFindInFileAction("abc", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind variable usage and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(7, 0)
        performFindInFileAction("fiel", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind method usage and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(5, 0)
        performFindInFileAction("main", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testFind type usage and don't get suggestion`() {
        val fromOffset = logicalPositionToOffset(5, 0)
        performFindInFileAction("Static", fromOffset)
        focusEditor()

        invokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}