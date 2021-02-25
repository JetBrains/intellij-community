package org.jetbrains.kotlin.idea.imports

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import org.jetbrains.kotlin.idea.codeInsight.KotlinCodeInsightSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

class AutoImportTest : KotlinLightCodeInsightFixtureTestCase() {
    fun `test with auto-import`() = doTest(true)
    fun `test without auto-import`() = doTest(false)

    private fun doTest(withAutoImport: Boolean) {
        myFixture.addFileToProject(
            "a/b/JavaClass.java",
            """
                package a.b;
                
                public class JavaClass {}
            """.trimIndent()
        )

        myFixture.configureByText(
            "Main.kt",
            """
                package my

                fun test() {
                    val javaClass = Java<caret>Class()
                }
            """.trimIndent()
        )

        val settings = KotlinCodeInsightSettings.getInstance()
        val oldValue = settings.addUnambiguousImportsOnTheFly
        try {
            settings.addUnambiguousImportsOnTheFly = withAutoImport
            DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file)
        } finally {
            settings.addUnambiguousImportsOnTheFly = oldValue
        }

        myFixture.checkResult(
            """
                |package my${if (withAutoImport) "\n\n|import a.b.JavaClass" else ""}
                |
                |fun test() {
                |    val javaClass = JavaClass()
                |}
        """.trimMargin()
        )
    }
}
