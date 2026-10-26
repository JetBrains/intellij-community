// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script

import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.core.script.definition.ScriptEditorToolbarProvider
import org.jetbrains.kotlin.idea.core.script.definition.editorToolbar
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.templates.ScriptWithArgs

class ReloadScriptConfigurationActionTest : KotlinLightCodeInsightFixtureTestCase() {

    private val action = ReloadScriptConfigurationAction()

    fun `test reload action is visible by default`() {
        myFixture.configureByText("test.kts", "val x = 1")
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)

        assertTrue("Action should be visible by default", event.presentation.isVisible)
        assertTrue("Action should be enabled by default", event.presentation.isEnabled)
    }

    fun `test reload action is hidden without editor toolbar`() {
        registerCustomDefinition(editorToolbar = null)

        myFixture.configureByText("test.custom.kts", "val x = 1")
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)

        assertFalse("Action should be hidden without the editor toolbar", event.presentation.isVisible)
    }

    fun `test reload action is visible with custom editor toolbar`() {
        registerCustomDefinition(editorToolbar = { _, _ -> null })

        myFixture.configureByText("test.custom.kts", "val x = 1")
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)

        assertTrue("Action should be visible with the editor toolbar", event.presentation.isVisible)
        assertTrue("Action should be enabled with the editor toolbar", event.presentation.isEnabled)
    }

    private fun registerCustomDefinition(editorToolbar: ScriptEditorToolbarProvider?) {
        val customDefinition = createScriptDefinitionFromTemplate(
            KotlinType(ScriptWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("custom.kts")
                ide.editorToolbar(editorToolbar)
            }
        )

        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "ReloadScriptConfigurationActionTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(customDefinition)
            },
            testRootDisposable,
        )

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
