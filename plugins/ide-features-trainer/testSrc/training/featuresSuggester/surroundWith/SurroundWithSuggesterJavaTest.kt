package training.featuresSuggester.surroundWith

import junit.framework.TestCase
import training.featuresSuggester.NoSuggestion

class SurroundWithSuggesterJavaTest : SurroundWithSuggesterTest() {

    override val testingCodeFileName: String = "JavaCodeExample.java"

    override fun `testSurround one statement with IF and get suggestion`() {
        insertNewLineAt(6)
        type("if () {")
        insertNewLineAt(8)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround 2 statements with IF and add '}' at the line with second statement and get suggestion`() {
        insertNewLineAt(5)
        type("if (true){")
        moveCaretToLogicalPosition(7, 20)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround all statements in block with IF and get suggestion`() {
        insertNewLineAt(5)
        type("if(){")
        insertNewLineAt(14)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround one statement with IF in one line and get suggestion`() {
        moveCaretToLogicalPosition(6, 8)
        type("if(1 > 2 ){")
        moveCaretRelatively(12, 0)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround statements with FOR and get suggestion`() {
        insertNewLineAt(6)
        type("for (int i = 0; i < 10; i++) {")
        insertNewLineAt(13)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    override fun `testSurround statements with WHILE and get suggestion`() {
        insertNewLineAt(7)
        type("while(false && true){")
        insertNewLineAt(10)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    fun `testSurround IfStatement with IF and get suggestion`() {
        insertNewLineAt(9)
        type("if (false && true){")
        insertNewLineAt(13)
        type("}")

        testInvokeLater {
            assertSuggestedCorrectly()
        }
    }

    /**
     * This case must throw suggestion but not working now
     */
    fun `testSurround statements with FOREACH and don't get suggestion`() {
        insertNewLineAt(6)
        type("for (int a: list) {")
        insertNewLineAt(13)
        type("}")

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testSurround 0 statements with IF and don't get suggestion`() {
        insertNewLineAt(6)
        type("if (true) {    }")

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }

    override fun `testWrite if() but add braces in another place and don't get suggestion`() {
        insertNewLineAt(6)
        type("if() ")
        moveCaretToLogicalPosition(7, 20)
        type("{")
        moveCaretToLogicalPosition(9, 23)
        type("}")

        testInvokeLater {
            TestCase.assertTrue(expectedSuggestion is NoSuggestion)
        }
    }
}
