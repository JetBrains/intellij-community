// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.TestActionEvent
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

class KotlinScriptDefinitionWidgetActionProviderTest : KotlinLightCodeInsightFixtureTestCase(), ExpectedPluginModeProvider {
    override val pluginMode: KotlinPluginMode = KotlinPluginMode.K2

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    fun `test createAction returns null for non-script file`() {
        myFixture.configureByText("Test.kt", "fun main() {}")
        assertNull(KotlinScriptDefinitionInfoWidgetProvider().createAction(myFixture.editor))
    }

    fun `test kts presentation text is dot kts`() {
        myFixture.configureByText("test.kts", "val x = 1")
        val action = KotlinScriptDefinitionInfoWidgetProvider().createAction(myFixture.editor)
            ?: error("Action must not be null for .kts file")
        val event = createActionEvent()
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
        assertNotNull(event.presentation.icon)
        assertEquals(".kts", event.presentation.text)
    }

    fun `test main kts presentation is visible`() {
        myFixture.configureByText("test.main.kts", "val x = 1")
        val action = KotlinScriptDefinitionInfoWidgetProvider().createAction(myFixture.editor)
            ?: error("Action must not be null for .main.kts file")
        val event = createActionEvent()
        action.update(event)

        assertTrue(event.presentation.isEnabledAndVisible)
        assertNotNull(event.presentation.icon)
        assertEquals(".main.kts", event.presentation.text)
    }

    fun `test presentation is hidden when editor shows a non-script file`() {
        myFixture.configureByText("test.kts", "val x = 1")
        val action = KotlinScriptDefinitionInfoWidgetProvider().createAction(myFixture.editor)
            ?: error("Action must not be null for .kts file")

        myFixture.configureByText("Test.kt", "fun main() {}")
        val event = createActionEvent()
        action.update(event)

        assertFalse(event.presentation.isEnabledAndVisible)
    }

    private fun createActionEvent(): AnActionEvent {
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.EDITOR, myFixture.editor)
            .add(CommonDataKeys.PSI_FILE, myFixture.file)
            .build()
        return TestActionEvent.createTestEvent(dataContext)
    }
}
