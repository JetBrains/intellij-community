// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.formatter

import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.base.test.configureCodeStyleAndRun
import org.jetbrains.kotlin.idea.formatter.kotlinCustomSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.util.application.executeWriteCommand
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

/**
 * Tests for stripping trailing whitespaces in KDoc comments, and for leaving every other kind of
 * comment untouched.
 * This is intentionally not using testdata because trailing whitespaces are stripped when comparing the files.
 */
@RunWith(JUnit38ClassRunner::class)
class K2CommentTrailingWhitespaceFormatterTest : KotlinLightCodeInsightFixtureTestCase() {

    fun testTrailingWhitespaceRemovedOnEveryKDocLine() {
        val before =
            "/**\n" +
            " * Summary line.   \n" +
            " *   \n" +
            " * Detailed text.\t\n" +
            " *\n" +
            " * @param value the value \n" +
            " * @return the result \n" +
            " */\n" +
            "fun foo(value: Int): Int = value"

        val expected =
            "/**\n" +
            " * Summary line.\n" +
            " *\n" +
            " * Detailed text.\n" +
            " *\n" +
            " * @param value the value\n" +
            " * @return the result\n" +
            " */\n" +
            "fun foo(value: Int): Int = value"

        doReformatTest(before, expected)
    }

    fun testTrailingWhitespaceIsKeptWhenKDocFormattingIsDisabled() {
        val text =
            "/**\n" +
            " * Summary line.   \n" +
            " *   \n" +
            " * @param value the value \n" +
            " */\n" +
            "fun foo(value: Int): Int = value"

        configureCodeStyleAndRun(
            project,
            configurator = { it.kotlinCustomSettings.ENABLE_KDOC_FORMATTING = false },
        ) {
            doReformatTest(text, text)
        }
    }

    fun testTrailingWhitespaceAfterMarkdownLinks() {
        val before =
            "/**\n" +
            " * See [Foo]  \n" +
            " * [com.example.Bar]  \n" +
            " * Use [Foo] and [Bar]  \n" +
            " * Ref [foo][bar]  \n" +
            " */\n" +
            "fun foo() {}"

        val expected =
            "/**\n" +
            " * See [Foo]\n" +
            " * [com.example.Bar]\n" +
            " * Use [Foo] and [Bar]\n" +
            " * Ref [foo][bar]\n" +
            " */\n" +
            "fun foo() {}"

        doReformatTest(before, expected)
    }

    fun testTrailingWhitespaceInBlockTagsWithLinks() {
        val before =
            "/**\n" +
            " * Summary \n" +
            " *\n" +
            " * @param[value] the value \n" +
            " * @return [Foo] \n" +
            " * @see [Bar] \n" +
            " * @throws IllegalStateException on error \n" +
            " */\n" +
            "fun foo(value: Int): Int = value"

        val expected =
            "/**\n" +
            " * Summary\n" +
            " *\n" +
            " * @param[value] the value\n" +
            " * @return [Foo]\n" +
            " * @see [Bar]\n" +
            " * @throws IllegalStateException on error\n" +
            " */\n" +
            "fun foo(value: Int): Int = value"

        doReformatTest(before, expected)
    }

    fun testTrailingWhitespaceInIndentedCodeBlock() {
        val before =
            "/**\n" +
            " * Example:\n" +
            " *\n" +
            " *     val a = 1  \n" +
            " *     println(a)  \n" +
            " */\n" +
            "fun foo() {}"

        val expected =
            "/**\n" +
            " * Example:\n" +
            " *\n" +
            " *     val a = 1\n" +
            " *     println(a)\n" +
            " */\n" +
            "fun foo() {}"

        doReformatTest(before, expected)
    }

    fun testTrailingWhitespaceInFencedCodeBlock() {
        val before =
            "/**\n" +
            " * ```  \n" +
            " * val a = 1  \n" +
            " * ```  \n" +
            " */\n" +
            "fun foo() {}"

        val expected =
            "/**\n" +
            " * ```\n" +
            " * val a = 1\n" +
            " * ```\n" +
            " */\n" +
            "fun foo() {}"

        doReformatTest(before, expected)
    }

    fun testInteriorWhitespaceAndOpeningLinePreserved() {
        val before =
            "/**  \n" +
            " * a  b  \n" +
            " * [Foo]bar  \n" +
            " */\n" +
            "fun foo() {}"

        val expected =
            "/**\n" +
            " * a  b\n" +
            " * [Foo]bar\n" +
            " */\n" +
            "fun foo() {}"

        doReformatTest(before, expected)
    }

    fun testCleanKDocWithLinksIsUnchanged() {
        val text =
            "/**\n" +
            " * Summary with [Foo].\n" +
            " *\n" +
            " * @param value see [Bar]\n" +
            " * @return [Baz]\n" +
            " */\n" +
            "fun foo(value: Int): Int = value"

        doReformatTest(text, text)
    }

    fun testSingleLineKDocWithLinkIsUnchanged() {
        val text =
            "/** [Foo] */\n" +
            "fun foo() {}"

        doReformatTest(text, text)
    }

    fun testMultilineBlockCommentIsNotChanged() {
        val text =
            "/*\n" +
            " * Block comment summary  \n" +
            " * second line\t\n" +
            " *  \n" +
            " * last line  \n" +
            " */\n" +
            "fun foo() {}"

        doReformatTest(text, text)
    }

    fun testMultilineBlockCommentWithoutAsterisksIsNotChanged() {
        val text =
            "/*\n" +
            "   first  \n" +
            "   second\t\n" +
            "*/\n" +
            "fun foo() {}"

        doReformatTest(text, text)
    }

    fun testSingleLineLineCommentIsNotChanged() {
        // A trailing whitespace in a single-line `//` comment is part of the comment token and must be left as-is.
        val text =
            "// a single-line comment  \n" +
            "fun foo() {}"

        doReformatTest(text, text)
    }

    fun testConsecutiveSingleLineCommentsAreNotChanged() {
        val text =
            "// first line  \n" +
            "// second line  \n" +
            "fun foo() {}"

        doReformatTest(text, text)
    }

    fun testSingleLineBlockCommentIsNotChanged() {
        val text =
            "/* a single-line block comment */\n" +
            "fun foo() {}"

        doReformatTest(text, text)
    }

    fun testBlockCommentInsideFunctionBodyIsNotChanged() {
        val text =
            "fun foo() {\n" +
            "    /*\n" +
            "     * inside body  \n" +
            "     * second line\t\n" +
            "     */\n" +
            "    println()\n" +
            "}"

        doReformatTest(text, text)
    }

    private fun doReformatTest(before: String, expected: String) {
        val file = myFixture.configureByText("A.kt", before)
        project.executeWriteCommand("Reformat") {
            CodeStyleManager.getInstance(project).reformat(file)
        }

        assertEquals(expected, file.text)
    }
}
