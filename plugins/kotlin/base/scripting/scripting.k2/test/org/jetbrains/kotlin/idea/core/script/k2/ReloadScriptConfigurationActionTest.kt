// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2

import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.shared.definition.reloadable
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class ReloadScriptConfigurationActionTest : KotlinLightCodeInsightFixtureTestCase() {

    private val action = ReloadScriptConfigurationAction()

    fun `test reload action is visible by default`() {
        myFixture.configureByText("test.kts", "val x = 1")
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)

        assertTrue("Action should be visible by default", event.presentation.isVisible)
        assertTrue("Action should be enabled by default", event.presentation.isEnabled)
    }

    fun `test reload action is hidden when reloadable is false`() {
        registerCustomDefinition(reloadable = false)

        myFixture.configureByText("test.custom.kts", "val x = 1")
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)

        assertFalse("Action should be hidden when reloadable is false", event.presentation.isVisible)
    }

    fun `test reload action is visible when reloadable is explicitly true`() {
        registerCustomDefinition(reloadable = true)

        myFixture.configureByText("test.custom.kts", "val x = 1")
        val event = TestActionEvent.createTestEvent(action)
        action.update(event)

        assertTrue("Action should be visible when reloadable is true", event.presentation.isVisible)
        assertTrue("Action should be enabled when reloadable is true", event.presentation.isEnabled)
    }

    private fun registerCustomDefinition(reloadable: Boolean) {
        val (compilationConfiguration, evaluationConfiguration) = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension("custom.kts")
                ide.reloadable(reloadable)
            }
        )
        val customDefinition = ScriptDefinition.FromConfigurations(
            defaultJvmScriptingHostConfiguration,
            compilationConfiguration,
            evaluationConfiguration
        )

        project.registerExtension(SCRIPT_DEFINITIONS_SOURCES, object : ScriptDefinitionsSource {
            override val definitions: Sequence<ScriptDefinition> = sequenceOf(customDefinition)
        }, testRootDisposable)

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
