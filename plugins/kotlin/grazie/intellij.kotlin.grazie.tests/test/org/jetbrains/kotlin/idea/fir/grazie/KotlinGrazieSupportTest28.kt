// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.grazie

import com.intellij.grazie.grazieTestFixture
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor
import com.intellij.grazie.text.extractAllTexts
import com.intellij.grazie.text.unknownOffsets
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.Path

@TestApplication
class KotlinGrazieSupportTest28 {
    companion object {
        private val projectFixture = projectFixture(openAfterCreation = true)
        private val descriptor = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    private val grazie by grazieTestFixture(
        projectFixture,
        moduleTypeId = descriptor.moduleTypeId,
        testDataPath = Path(PlatformTestUtil.getCommunityPath(), "plugins/kotlin/idea/tests/testData").toString(),
        additionalEnabledRules = setOf("UPPERCASE_SENTENCE_START"),
    )

    @BeforeEach
    fun setUpModule() {
        val module = grazie.fixture.module
        val sdk = descriptor.sdk!!
        WriteAction.runAndWait<RuntimeException> { ProjectJdkTable.getInstance().addJdk(sdk, grazie.testRootDisposable) }
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        ModuleRootModificationUtil.updateModel(module) { descriptor.configureModule(module, it, it.contentEntries.single()) }
        IndexingTestUtil.waitUntilIndexesAreReady(grazie.project)
    }

    @Test
    fun `test spellcheck in constructs`() {
        grazie.runHighlightTestForFile("grazie/Constructs.kt")
    }

    @Test
    fun `test Kotlin keyword operator is not a typo`() {
        grazie.fixture.configureByText("a.kt", """
            fun f(low: Int, high: Int) {
                val mid = (low + high) ushr 1
            }
        """.trimIndent())
        grazie.fixture.checkHighlighting()
    }

    @Test
    fun `test grammar check in docs`() {
        grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN, Lang.RUSSIAN))
        grazie.runHighlightTestForFile("grazie/Docs.kt")
    }

    @Test
    fun `test grammar check in string literals`() {
        grazie.runHighlightTestForFile("grazie/StringLiterals.kt")
    }

    @Test
    fun `test umlauts doesn't produce false positives`() {
        grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
        grazie.runHighlightTestForFile("grazie/Umlauts.kt")
    }

    @Test
    fun `test no german warnings in kdoc with heading and slashes`() {
        grazie.enableProofreadingFor(setOf(Lang.GERMANY_GERMAN))
        grazie.fixture.configureByText("a.kt", """
            /**
             * # Titel
             * Hier ist ok // Außer am Satzanfang werden nur Nomen und Eigennamen großgeschrieben.
             */
            fun f() {}
        """.trimIndent())
        grazie.fixture.checkHighlighting()
    }

    @Test
    fun `test text extraction in string literals`() {
        val file = grazie.fixture.configureByText("a.kt", "val s = \"foo $" + "{injection} bar\" ")
        val content = runReadActionBlocking { TextExtractor.findTextAt(file, 10, TextContent.TextDomain.ALL) }
        assertEquals("foo | bar", unknownOffsets(content!!))
    }

    @Test
    fun `test text extraction in string literals with soft hyphen`() {
        val file = grazie.fixture.configureByText("a.kt", "val s = \"I just want to say \\u00ADhello to everyone.\"")
        val content = runReadActionBlocking { TextExtractor.findTextAt(file, 10, TextContent.TextDomain.ALL) }
        assertEquals("I just want to say |hello to everyone.", unknownOffsets(content!!))
    }

    @Test
    fun `test meaningful single suggestion in RenameTo action`() {
        grazie.fixture.configureByText("a.kt", """
            class A {
                // <TYPO descr="Typo: In word 'tagret'">tagret</TYPO>
                val <TYPO descr="Typo: In word 'tagret'">tag<caret>ret</TYPO> = 1
            }
        """)
        grazie.fixture.checkHighlighting()
        val intention = grazie.fixture.findSingleIntention("Typo: Rename to 'target'")
        grazie.fixture.launchAction(intention)
        grazie.fixture.checkResult("""
            class A {
                // target
                val target = 1
            }
        """)
    }

    @Test
    fun `test meaningful single suggestion in escaped identifier`() {
        grazie.fixture.configureByText("a.kt", """
            fun `test no <TYPO descr="Typo: In word 'typpo'">typpo</TYPO> around path <TYPO descr="Typo: In word 'typpo'">ty<caret>ppo</TYPO>`() {
            }
        """)
        grazie.fixture.checkHighlighting()
        val intention = grazie.fixture.findSingleIntention("Typo: Rename to 'test no typpo around path typo'")
        grazie.fixture.launchAction(intention)
        grazie.fixture.checkResult("""
            fun `test no typpo around path typo`() {
            }
        """)
    }

    @Test
    fun `test escape sequences in string literals`() {
        grazie.fixture.configureByText("a.kt", """
            val value2 = "class\nexpected 1\ttypo: 2"
            val value1 = ""${'"'}
                wrong object class\<TYPO descr="Typo: In word 'nexpected'">nexpected</TYPO> 1\<TYPO descr="Typo: In word 'ttypo'">ttypo</TYPO>: 2
            ""${'"'}""".trimIndent()
        )
        grazie.fixture.checkHighlighting()
    }

    @Test
    fun `test code-like fragments are not extracted`() {
        val texts = runReadActionBlocking { extractAllTexts("a.kt", $$"""
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
        """.trimIndent(), grazie.project) }
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
