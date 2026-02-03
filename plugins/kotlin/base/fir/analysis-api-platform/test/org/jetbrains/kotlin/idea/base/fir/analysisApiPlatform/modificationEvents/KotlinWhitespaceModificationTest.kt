// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.analysisApiPlatform.modificationEvents

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runUndoTransparentWriteAction
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.components.KaDiagnosticCheckerFilter
import org.jetbrains.kotlin.analysis.api.fir.diagnostics.KaFirDiagnostic
import org.jetbrains.kotlin.analysis.api.permissions.KaAllowAnalysisOnEdt
import org.jetbrains.kotlin.analysis.api.permissions.allowAnalysisOnEdt
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Whitespace modification is usually harmless but can affect diagnostics. See KT-82629.
 */
@OptIn(KaAllowAnalysisOnEdt::class)
class KotlinWhitespaceModificationTest : KotlinLightCodeInsightFixtureTestCase() {
    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    private val TOP_LEVEL_IF_ELSE_FILE_TEXT =
        """
            val s = if (true) "a"<caret>else "b"
        """.trimIndent()

    private val CLASS_IF_ELSE_FILE_TEXT =
        """
            class Foo {
                val s = if (true) "a"<caret>else "b"
            } 
        """.trimIndent()

    private val LOCAL_IF_ELSE_FILE_TEXT =
        """
            fun foo(): String {
                return if (true) "a"<caret>else "b"
            }
        """.trimIndent()

    private val TOP_LEVEL_DELEGATION_FILE_TEXT =
        """
            val s2 by<caret>"a"
        """.trimIndent()

    private val CLASS_DELEGATION_FILE_TEXT =
        """
            class Foo {
                val s2 by<caret>"a"
            }
        """.trimIndent()

    private val LOCAL_DELEGATION_FILE_TEXT =
        """
            fun foo() {
                val s2 by<caret>"a"
            }
        """.trimIndent()

    private val CLASS_INIT_FOR_IN_FILE_TEXT =
        """
            class Foo {
                init {
                    for (c in<caret>"abcdef") {
                        println(c)
                    }
                }
            }
        """.trimIndent()

    private val LOCAL_FOR_IN_FILE_TEXT =
        """
            fun foo() {
                for (c in<caret>"abcdef") {
                    println(c)
                }
            }
        """.trimIndent()

    fun `test top-level if-else literal whitespace diagnostics adding whitespace`() {
        // Target state: `if (true) "a" else "b"`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(TOP_LEVEL_IF_ELSE_FILE_TEXT)
    }

    fun `test top-level if-else literal whitespace diagnostics adding comment`() {
        // Target state: `if (true) "a"/**/else "b"`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(TOP_LEVEL_IF_ELSE_FILE_TEXT)
    }

    fun `test class if-else literal whitespace diagnostics adding whitespace`() {
        // Target state: `if (true) "a" else "b"`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(CLASS_IF_ELSE_FILE_TEXT)
    }

    fun `test class if-else literal whitespace diagnostics adding comment`() {
        // Target state: `if (true) "a"/**/else "b"`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(CLASS_IF_ELSE_FILE_TEXT)
    }

    fun `test local if-else literal whitespace diagnostics adding whitespace`() {
        // Target state: `return if (true) "a" else "b"`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(LOCAL_IF_ELSE_FILE_TEXT)
    }

    fun `test local if-else literal whitespace diagnostics adding comment`() {
        // Target state: `return if (true) "a"/**/else "b"`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(LOCAL_IF_ELSE_FILE_TEXT)
    }

    fun `test top-level delegation literal whitespace diagnostics adding whitespace`() {
        // Target state: `val s2 by "a"`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(TOP_LEVEL_DELEGATION_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test top-level delegation literal whitespace diagnostics adding comment`() {
        // Target state: `val s2 by/**/"a"`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(TOP_LEVEL_DELEGATION_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test class delegation literal whitespace diagnostics adding whitespace`() {
        // Target state: `val s2 by "a"`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(CLASS_DELEGATION_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test class delegation literal whitespace diagnostics adding comment`() {
        // Target state: `val s2 by/**/"a"`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(CLASS_DELEGATION_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test local delegation literal whitespace diagnostics adding whitespace`() {
        // Target state: `val s2 by "a"`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(LOCAL_DELEGATION_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test local delegation literal whitespace diagnostics adding comment`() {
        // Target state: `val s2 by/**/"a"`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(LOCAL_DELEGATION_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test class-init for-in literal whitespace diagnostics adding whitespace`() {
        // Target state: `for (c in "abcdef") { ... }`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(CLASS_INIT_FOR_IN_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test class-init for-in literal whitespace diagnostics adding comment`() {
        // Target state: `for (c in/**/"abcdef") { ... }`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(CLASS_INIT_FOR_IN_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test local for-in literal whitespace diagnostics adding whitespace`() {
        // Target state: `for (c in "abcdef") { ... }`.
        performLiteralWhitespaceDiagnosticsTestAddingWhitespace(LOCAL_FOR_IN_FILE_TEXT, selectTarget = { it.parent })
    }

    fun `test local for-in literal whitespace diagnostics adding comment`() {
        // Target state: `for (c in/**/"abcdef") { ... }`.
        performLiteralWhitespaceDiagnosticsTestAddingComment(LOCAL_FOR_IN_FILE_TEXT, selectTarget = { it.parent })
    }

    private fun performLiteralWhitespaceDiagnosticsTestAddingWhitespace(
        fileText: String,
        selectTarget: (PsiElement) -> PsiElement? = { it },
    ) =
        performLiteralWhitespaceDiagnosticsTest(fileText, selectTarget) {
            KtPsiFactory(project, markGenerated = false).createWhiteSpace()
        }

    private fun performLiteralWhitespaceDiagnosticsTestAddingComment(
        fileText: String,
        selectTarget: (PsiElement) -> PsiElement? = { it },
    ) =
        performLiteralWhitespaceDiagnosticsTest(fileText, selectTarget) {
            KtPsiFactory(project, markGenerated = false).createComment("/* */")
        }

    private fun performLiteralWhitespaceDiagnosticsTest(
        fileText: String,
        selectTarget: (PsiElement) -> PsiElement? = { it },
        createWhitespace: () -> PsiElement,
    ) = allowAnalysisOnEdt {
        val ktFile = myFixture.configureByText("file.kt", fileText) as KtFile

        // One error: Literal must be surrounded by whitespace.
        assertLiteralWhitespaceDiagnosticsCount(file = ktFile, expectedCount = 1)

        val whitespaceElement = runUndoTransparentWriteAction {
            val targetElement = file.findElementAt(myFixture.caretOffset)?.let(selectTarget)
                ?: error("Cannot find the target element.")

            val parent = targetElement.parent ?: error("Cannot find the parent of the target element.")
            parent.addBefore(createWhitespace(), targetElement)

            // When adding a space, the result of `addBefore` returns the closing quote (`"`) of the preceding string instead of the
            // whitespace element itself. The result of `createWhitespace()` is not guaranteed to be the actual added element either. So,
            // taken together, we have to fetch the new element manually.
            targetElement.prevSibling
        }

        // After inserting the whitespace, the error should be fixed.
        assertLiteralWhitespaceDiagnosticsCount(file = ktFile, expectedCount = 0)

        // Finally, we remove the whitespace again to check that the error reappears (modification detection in the other direction).
        runUndoTransparentWriteAction {
            whitespaceElement.delete()
        }
        assertLiteralWhitespaceDiagnosticsCount(file = ktFile, expectedCount = 1)
    }

    private fun assertLiteralWhitespaceDiagnosticsCount(file: KtFile, expectedCount: Int) {
        runReadAction {
            analyze(file) {
                // We only want to grab "literals must be surrounded by whitespace" diagnostics, which are `Unsupported`.
                val diagnostics = file
                    .collectDiagnostics(KaDiagnosticCheckerFilter.ONLY_COMMON_CHECKERS)
                    .filterIsInstance<KaFirDiagnostic.Unsupported>()

                assertEquals(expectedCount, diagnostics.size)
            }
        }
    }
}
