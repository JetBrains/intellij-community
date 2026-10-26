// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.notifications

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.SplitEditorToolbar
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.core.script.definition.ScriptEditorToolbarProvider
import org.jetbrains.kotlin.idea.core.script.definition.editorToolbar
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.templates.ScriptWithArgs

class ScriptEditorToolbarNotificationProviderTest : KotlinLightCodeInsightFixtureTestCase() {
    private val notificationProvider = ScriptEditorToolbarNotificationProvider()

    fun `test non-script file has no toolbar`() {
        myFixture.configureByText("Test.kt", "fun main() {}")
        assertNull(toolbar())
    }

    fun `test kts script has default toolbar with settings at the end`() {
        myFixture.configureByText("test.kts", "val x = 1")
        val toolbar = toolbar() as SplitEditorToolbar
        val settings = ActionManager.getInstance().getAction("KotlinScripts.OpenScriptingSettings")
        assertContainsElements((toolbar.rightToolbar.actionGroup as DefaultActionGroup).childActionsOrStubs.toList(), settings)
    }

    fun `test definition creates its own toolbar`() {
        val custom = JPanel()
        registerCustomDefinition { _, _ -> custom }
        myFixture.configureByText("test.custom.kts", "val x = 1")
        assertSame(custom, toolbar())
    }

    fun `test toolbar is created once per editor`() {
        var created = 0
        registerCustomDefinition { _, _ -> JPanel().also { created++ } }
        myFixture.configureByText("test.custom.kts", "val x = 1")
        assertSame(toolbar(), toolbar())
        assertEquals(1, created)
    }

    fun `test toolbar lifetime ends when provider changes`() {
        var disposed = false
        registerCustomDefinition { _, lifetime -> JPanel().also { Disposer.register(lifetime) { disposed = true } } }
        myFixture.configureByText("test.custom.kts", "val x = 1")
        assertNotNull(toolbar())
        assertFalse(disposed)

        registerCustomDefinition(null)
        assertNull(toolbar())
        assertTrue(disposed)
    }

    fun `test definition without provider has no toolbar`() {
        registerCustomDefinition(null)
        myFixture.configureByText("test.custom.kts", "val x = 1")
        assertNull(toolbar())
    }

    fun `test provider without component has no toolbar`() {
        registerCustomDefinition { _, _ -> null }
        myFixture.configureByText("test.custom.kts", "val x = 1")
        assertNull(toolbar())
    }

    private fun toolbar(): JComponent? {
        val data = notificationProvider.collectNotificationData(project, myFixture.file.virtualFile) ?: return null
        return data.apply(TextEditorProvider.getInstance().getTextEditor(myFixture.editor))
    }

    private var customProvider: ScriptEditorToolbarProvider? = null
    private var isProviderRegistered = false

    /** A second call replaces the definition of `*.custom.kts` files. */
    private fun registerCustomDefinition(provider: ScriptEditorToolbarProvider?) {
        customProvider = provider
        if (!isProviderRegistered) {
            isProviderRegistered = true
            project.registerExtension(
                ScriptDefinitionsProvider.EP_NAME,
                object : ScriptDefinitionsProvider {
                    override val id: String = "ScriptEditorToolbarProviderTest"
                    override fun provideDefinitions(
                        baseHostConfiguration: ScriptingHostConfiguration,
                        loadedScriptDefinitions: List<ScriptDefinition>,
                    ): Iterable<ScriptDefinition> = listOf(
                        createScriptDefinitionFromTemplate(
                            KotlinType(ScriptWithArgs::class),
                            defaultJvmScriptingHostConfiguration,
                            compilation = {
                                fileExtension("custom.kts")
                                ide.editorToolbar(customProvider)
                            }
                        )
                    )
                },
                testRootDisposable,
            )
        }

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
