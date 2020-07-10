package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.testFramework.runInEdtAndWait

@Deprecated("Tests must run only in EDT")
class LineCommentingSuggesterTest : FeatureSuggesterTest() {

    fun `testComment one line and get suggestion`() {
        testSuggestionFound({
            myFixture.apply {
                configureByFile("SimpleCodeExample.java")
                type("//")
            }
        }, {
            it.message == LineCommentingSuggester.POPUP_MESSAGE
        })
    }

    fun `testType one slash and dont get suggestion`() {
        testSuggestionNotFound {
            myFixture.apply {
                configureByFile("SimpleCodeExample.java")
                type("/")
            }
        }
    }

    // todo: Do we need to suggest when commenting one line from multiline statement?
    fun `testComment one line from multiline statement and dont get suggestion`() {
        testSuggestionNotFound {
            myFixture.apply {
                configureByFile("SimpleCodeExample.java")
                runInEdtAndWait {
                    editor.caretModel.moveCaretRelatively(0, 2, false, false, false)
                }
                type("//")
            }
        }
    }

    // todo: add tests for removing '//' suggestion (needed method that can delete characters from caret position)

}