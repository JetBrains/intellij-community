// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.parameterInfo

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.java.codeInsight.AbstractParameterInfoTestCase
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.AutoPopupParameterInfoTestUtil
import com.intellij.testFramework.fixtures.EditorHintFixture
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.runAll
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class)
class FirParameterInfoAutoPopupTest : KotlinLightCodeInsightFixtureTestCase() {
    private lateinit var hintFixture: EditorHintFixture
    private var storedParameterInfoDelay = 0

    override fun setUp() {
        super.setUp()
        hintFixture = EditorHintFixture(testRootDisposable)
        storedParameterInfoDelay = CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY
        CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = 0
    }

    override fun tearDown() {
        runAll(
            { CodeInsightSettings.getInstance().PARAMETER_INFO_DELAY = storedParameterInfoDelay },
            { super.tearDown() },
        )
    }

    fun testCommaInArgumentList() = doTest(
        ',',
        """
            fun foo(a: Int, b: Int) {}
            fun test() {
                foo(1<caret>)
            }
        """,
        isHintExpected = true,
    )

    fun testCommaInEndOfLineCommentInsideLambda() = doTest(
        ',',
        """
            fun foo(f: () -> Unit) {}
            fun test() {
                foo {
                    // <caret>
                }
            }
        """,
        isHintExpected = false,
    )

    fun testCommaInBlockCommentInsideLambda() = doTest(
        ',',
        """
            fun foo(f: () -> Unit) {}
            fun test() {
                foo {
                    /* <caret> */
                }
            }
        """,
        isHintExpected = false,
    )

    fun testCommaInKDocInsideLambda() = doTest(
        ',',
        """
            fun foo(f: () -> Unit) {}
            fun test() {
                foo {
                    /** <caret> */
                }
            }
        """,
        isHintExpected = false,
    )

    fun testCommaAfterBlockCommentInArgumentList() = doTest(
        ',',
        """
            fun foo(a: Int, b: Int) {}
            fun test() {
                foo(1 /* first */<caret>)
            }
        """,
        isHintExpected = true,
    )

    fun testCommaAfterKDocInArgumentList() = doTest(
        ',',
        """
            fun foo(a: Int, b: Int) {}
            fun test() {
                foo(1 /** first */<caret>)
            }
        """,
        isHintExpected = true,
    )

    fun testCommaAfterCharacterLiteralInArgumentList() = doTest(
        ',',
        """
            fun foo(a: Char, b: Char) {}
            fun test() {
                foo('a'<caret>)
            }
        """,
        isHintExpected = true,
    )

    fun testCommaInBlankKDocLineInsideLambda() = doTest(
        ',',
        """
            fun foo(f: () -> Unit) {}
            fun test() {
                foo {
                    /**
                    <caret>
                    */
                }
            }
        """,
        isHintExpected = false,
    )

    fun testCommaReplacesSelectionThatEndsInComment() = doTest(
        ',',
        """
            fun foo(a: Int, b: Int) {}
            fun test() {
                foo(1<selection>, 2 // note<caret></selection>
                )
            }
        """,
        isHintExpected = true,
    )

    fun testExplicitParameterInfoInCommentInsideLambda() {
        myFixture.configureByText(
            "a.kt",
            """
                fun foo(f: () -> Unit) {}
                fun test() {
                    foo {
                        // <caret>
                    }
                }
            """.trimIndent()
        )
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SHOW_PARAMETER_INFO)
        AbstractParameterInfoTestCase.waitForParameterInfo()

        assertNotNull("The parameter info is expected to show", hintFixture.currentHintText)
    }

    fun testLeftParenthesisInCommentInsideLambda() = doTest(
        '(',
        """
            fun foo(f: () -> Unit) {}
            fun test() {
                foo {
                    // bar<caret>
                }
            }
        """,
        isHintExpected = false,
    )

    fun testLeftBraceInCommentInsideLambda() = doTest(
        '{',
        """
            fun foo(f: () -> Unit) {}
            fun test() {
                foo {
                    // <caret>
                }
            }
        """,
        isHintExpected = false,
    )

    private fun doTest(c: Char, text: String, isHintExpected: Boolean) {
        myFixture.configureByText("a.kt", text.trimIndent())
        myFixture.type(c)
        AutoPopupParameterInfoTestUtil.waitForAutoPopup(project)
        AbstractParameterInfoTestCase.waitForParameterInfo()

        val hintText = hintFixture.currentHintText
        if (isHintExpected) {
            assertNotNull("The parameter info is expected to pop up", hintText)
        } else {
            assertNull("The parameter info is not expected to pop up", hintText)
        }
    }
}
