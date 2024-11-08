// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.fir.actions

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.event.MarkupModelListener
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl
import com.intellij.util.concurrency.AppExecutorUtil
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightSettings
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.psi.KtFile
import org.junit.runner.RunWith

@RunWith(JUnit3RunnerWithInners::class)
class K2AutoImportHighlightingFlickeringTest: KotlinLightCodeInsightFixtureTestCase() {

    override val pluginMode: KotlinPluginMode
        get() = KotlinPluginMode.K2

    override fun getDefaultProjectDescriptor(): KotlinLightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun `test highlighting does not flicker during auto-import`() {
        // type in the editor some not-imported class name along with some other unresolved references
        // and check that after that class was auto-imported, all other "unresolved references" diagnostics remain untouched
        @Language("kotlin")
        val fileText = """
            class AutoI {
                fun foo() {
                    <caret>
                }
            }
        """.trimIndent()
        withCustomCompilerOptions(fileText, project, module) {
            val fixture = myFixture as CodeInsightTestFixtureImpl
            fixture.canChangeDocumentDuringHighlighting(true)
            val settings = KotlinCodeInsightSettings.getInstance()
            val oldSetting = settings.addUnambiguousImportsOnTheFly
            try {
                settings.addUnambiguousImportsOnTheFly = true

                fixture.configureByText("x.kt", fileText)

                // assert no highlighters for "moduleA" ever got removed from the editor
                val markupModel = DocumentMarkupModel.forDocument(editor.document, project, true) as MarkupModelEx
                markupModel.addMarkupModelListener(testRootDisposable, object : MarkupModelListener {
                    override fun beforeRemoved(highlighter: RangeHighlighterEx) {
                        val s = textHighlighted(highlighter)
                        assertFalse(
                            "Highlighting must not spuriously remove highlighting during auto-import because it leads to flickers in the editor",
                            "moduleA" == s
                        )
                    }
                })

                UsefulTestCase.assertEmpty(fixture.doHighlighting(HighlightSeverity.ERROR))

                fixture.type("FileInputStream(moduleA)")

                val errors = fixture.doHighlighting(HighlightSeverity.ERROR)
                UsefulTestCase.assertNotEmpty(errors)
                assertTrue(errors.any { info -> "moduleA" == textHighlighted(info.highlighter) })

                AppExecutorUtil.getAppExecutorService().submit {
                    DaemonCodeAnalyzerImpl.waitForUnresolvedReferencesQuickFixesUnderCaret(myFixture.file, myFixture.editor)
                }.get()

                val imports = (myFixture.file as KtFile).importDirectives
                assertTrue(fixture.file.text, imports.any { directive -> directive.text.contains("java.io.FileInputStream") })

                val errors2 = fixture.doHighlighting(HighlightSeverity.ERROR)
                assertTrue(errors2.any { info -> "moduleA" == textHighlighted(info.highlighter) })
            } finally {
                settings.addUnambiguousImportsOnTheFly = oldSetting
            }
        }
    }

    private fun textHighlighted(highlighter: RangeHighlighterEx): String =
        highlighter.textRange.substring(editor.document.text + " ".repeat(1000))
}