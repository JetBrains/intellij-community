package org.jetbrains.kotlin.idea.run

import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.testFramework.DumbModeTestUtils
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase

/**
 * Test that "run class" is calculated correctly and is available in Dumb Mode.
 */
class KotlinRunClassDumbModeTest : KotlinLightCodeInsightFixtureTestCase() {
    fun testTopLevelMain() {
        doTest(
            code = """
            fun <caret>main(args: Array<String>) {
              // yes
            }""".trimIndent(),
            mainClassName = "MainKt",
            runClassName = "MainKt",
        )
    }

    fun testNestedMain() {
        doTest(
            code = """ 
            class Main {
              class Nested {
                companion object {
                  @JvmStatic
                  fun <caret>main(args: Array<String>) {
                    println("hi")
                  }
                }
              }
            }""".trimIndent(),
            mainClassName = "Main.Nested",
            runClassName = $$"Main$Nested",
        )
    }

    private fun doTest(@Language("kotlin") code: String, mainClassName: String, runClassName: String) {
        myFixture.configureByText("main.kt", code)
        // TODO(bartekpacia): Find a way to attach actual Kotlin stdlib in JVM flavor instead of creating a dummy stub like below.
        myFixture.addFileToProject("kotlin/jvm/JvmStatic.kt", "package kotlin.jvm public annotation class JvmStatic")

        val gutterMarks = myFixture.findGuttersAtCaret()
        assertEquals(1, gutterMarks.size)
        val renderer = gutterMarks.first() as GutterIconRenderer
        val group = renderer.popupMenuActions!!

        val factory = PresentationFactory()
        val actions = Utils.expandActionGroup(group, factory, DataContext.EMPTY_CONTEXT, ActionPlaces.UNKNOWN, ActionUiKind.NONE)
            .filter { action ->
                val text = factory.getPresentation(action).text ?: return@filter false
                text.startsWith("Run '") && text.endsWith("'")
            }

        assertEquals(actions.toString(), 1, actions.size)

        // Run the configuration while in Dumb Mode.
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            myFixture.testAction(actions[0])
            NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        }

        val selected: RunnerAndConfigurationSettings = RunManager.getInstance(project).selectedConfiguration!!
        val runConfiguration = selected.configuration
        assertInstanceOf(runConfiguration, KotlinRunConfiguration::class.java)

        val kotlinConf = runConfiguration as KotlinRunConfiguration

        assertEquals(mainClassName, kotlinConf.mainClassName)

        // Verify that we can resolve the runClass while in Dumb Mode.
        DumbModeTestUtils.runInDumbModeSynchronously(project) {
            assertEquals(runClassName, kotlinConf.runClass)
        }
    }
}
