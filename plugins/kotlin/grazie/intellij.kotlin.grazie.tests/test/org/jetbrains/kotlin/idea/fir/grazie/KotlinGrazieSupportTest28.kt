// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.grazie

import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextContentTest
import com.intellij.grazie.text.TextExtractionTest
import com.intellij.grazie.text.TextExtractor
import com.intellij.testFramework.LightProjectDescriptor
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor

class KotlinGrazieSupportTest28 : GrazieTestBase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    override val additionalEnabledRules: Set<String> = setOf("UPPERCASE_SENTENCE_START")

    override fun runHighlightTestForFile(file: String) {
        myFixture.configureByFile(file)
        myFixture.checkHighlighting(true, false, false, false)
    }

    override fun getBasePath(): String = "community/plugins/kotlin/idea/tests/testData"

    fun `test spellcheck in constructs`() {
        runHighlightTestForFile("grazie/Constructs.kt")
    }

    fun `test grammar check in docs`() {
        enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
        runHighlightTestForFile("grazie/Docs.kt")
    }

    fun `test grammar check in string literals`() {
        runHighlightTestForFile("grazie/StringLiterals.kt")
    }

    fun `test umlauts doesn't produce false positives`() {
        enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
        runHighlightTestForFile("grazie/Umlauts.kt")
    }

    fun `test no german warnings in kdoc with heading and slashes`() {
        enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
        myFixture.configureByText("a.kt", """
            /**
             * # Titel
             * Hier ist ok // Außer am Satzanfang werden nur Nomen und Eigennamen großgeschrieben.
             */
            fun f() {}
        """.trimIndent())
        myFixture.checkHighlighting()
    }

    fun `test text extraction in string literals`() {
        val file = myFixture.configureByText("a.kt", "val s = \"foo $" + "{injection} bar\" ")
        val content = TextExtractor.findTextAt(file, 10, TextContent.TextDomain.ALL)
        assertEquals("foo | bar", TextContentTest.unknownOffsets(content))
    }

    fun `test meaningful single suggestion in RenameTo action`() {
        myFixture.configureByText("a.kt", """
            class A {
                // <TYPO descr="Typo: In word 'tagret'">tagret</TYPO>
                val <TYPO descr="Typo: In word 'tagret'">tag<caret>ret</TYPO> = 1
            }
        """)
        myFixture.checkHighlighting()
        val intention = myFixture.findSingleIntention("Typo: Rename to 'target'")
        myFixture.launchAction(intention)
        myFixture.checkResult("""
            class A {
                // target
                val target = 1
            }
        """)
    }

    fun `test escape sequences in string literals`() {
        myFixture.configureByText("a.kt", """
            val value2 = "class\nexpected 1\ttypo: 2"
            val value1 = ""${'"'}
                wrong object class\<TYPO descr="Typo: In word 'nexpected'">nexpected</TYPO> 1\<TYPO descr="Typo: In word 'ttypo'">ttypo</TYPO>: 2
            ""${'"'}""".trimIndent()
        )
        myFixture.checkHighlighting()
    }

    fun `test code-like fragments are not extracted`() {
        val texts = TextExtractionTest.extractAllTexts("a.kt", $$"""
            /**
             * The Markdown lexer folds a nested list item's leading indentation into its `LIST_BULLET`/`LIST_NUMBER` token
             * (e.g. `"  - "`), so a list / list-item block would otherwise start inside the line's indentation. Trim that
             * leading whitespace here so the block starts at its real content; otherwise offset-based consumers such as
             * indent auto-detection (`FormatterBasedLineIndentInfoBuilder`) undercount the indent of nested list lines.
             *
             * With code fence:
             * ```
             * fun String.helloWorld() {
             *     println("Hello World, $this")
             * }
             * ```
             * With indentation: 
             *
             *      fun String.helloWorld() {
             *         println("Hello World, $this")
             *      }
             *
             * With tilde:
             * ~~~
             * fun String.helloWorld() {
             *     println("Hello World, $this")
             * }
             * ~~~
             *
             * With backticks:
             * `fun main() { println("Hello, Kotlin") }`
             */
            fun main() {}
        """.trimIndent(), project)
        assertEquals(1, texts.size)
        assertEquals(texts.first().toString(), """
            The Markdown lexer folds a nested list item's leading indentation into its / token
            (e.g. ), so a list / list-item block would otherwise start inside the line's indentation. Trim that
            leading whitespace here so the block starts at its real content; otherwise offset-based consumers such as
            indent auto-detection () undercount the indent of nested list lines.

            With code fence:

            With indentation:





            With tilde:


            With backticks:

        """.trimIndent())
    }
}