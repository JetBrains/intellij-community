// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.extensions.ExtensionPoint
import com.intellij.openapi.extensions.ExtensionPoint.Kind
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.registerServiceInstance
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionPersistentSettings
import org.jetbrains.kotlin.idea.core.script.k2.settings.ScriptDefinitionPersistentSettings.ScriptDefinitionSetting
import org.jetbrains.kotlin.idea.core.script.shared.SCRIPT_DEFINITIONS_SOURCES
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration

class ScriptDefinitionProviderImplTest : LightPlatformTestCase(), ExpectedPluginModeProvider {
    override val pluginMode = KotlinPluginMode.K2

    private lateinit var provider: ScriptDefinitionProvider
    private lateinit var settings: ScriptDefinitionPersistentSettings
    private lateinit var definitionsSourcesPoint: ExtensionPoint<ScriptDefinitionsSource>

    override fun setUp() = setUpWithKotlinPlugin(testRootDisposable) {
        super.setUp()


        provider = ScriptDefinitionProviderImpl(project)

        getOrRegisterExtensionPoint(
            "org.jetbrains.kotlin.scripting.definitions.scriptDefinitionProvider",
            ScriptDefinitionProvider::class.java,
        ).apply {
            registerExtension(provider, testRootDisposable)
        }

        definitionsSourcesPoint = getOrRegisterExtensionPoint(SCRIPT_DEFINITIONS_SOURCES.name, ScriptDefinitionsSource::class.java)
        settings = ScriptDefinitionPersistentSettings(project)

        project.registerServiceInstance(KotlinScriptingSettings::class.java, settings)
    }

    private fun ExtensionPoint<ScriptDefinitionsSource>.addDefinitionWithSource(vararg definitions: ScriptDefinition) {
        registerExtension(
            object : ScriptDefinitionsSource {
                override val definitions: Sequence<ScriptDefinition>
                    get() = definitions.asSequence()
            },
            testRootDisposable
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    private fun <T : Any> getOrRegisterExtensionPoint(name: String, extensionClass: Class<T>): ExtensionPoint<T> {
        if (!project.extensionArea.hasExtensionPoint(name)) {
            project.extensionArea.registerExtensionPoint(name, extensionClass.name, Kind.INTERFACE, false)
        }

        return project.extensionArea.getExtensionPoint(name)
    }

    fun testFileExtensions() {
        val kt = TestSource("test.kt")
        val java = TestSource("Foo.java")
        assertFalse(provider.isScript(kt))
        assertFalse(provider.isScript(java))

        assertNull(provider.findDefinition(kt))
        assertNull(provider.findDefinition(java))

        val extensions = provider.getKnownFilenameExtensions().toSet()
        assertTrue("kts" in extensions)
    }

    fun testShouldIncludeRegisteredDefinition() {
        val definition = TestDefinition("definition1")
        definitionsSourcesPoint.addDefinitionWithSource(definition)
        assertTrue(provider.currentDefinitions.contains(definition))
    }

    fun testDefaultDefinition() {
        assertEquals("ideBundledScriptDefinition", provider.getDefaultDefinition().definitionId)
    }

    fun testDefinitionOrderShouldAffectsSearch() {
        val defA = TestDefinition("A")
        val defB = TestDefinition("B")
        definitionsSourcesPoint.addDefinitionWithSource(defA, defB)
        val testSource = TestSource("any.kts")

        // By default A should come first
        assertEquals("A", provider.findDefinition(testSource)?.definitionId)

        // Change order using ScriptDefinition.order property (lower comes first)
        defB.order = Int.MIN_VALUE
        defA.order = Int.MAX_VALUE
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()

        // After order update, B should come first
        assertEquals("B", provider.findDefinition(testSource)?.definitionId)
    }

    fun testNonMatchingDefinitionShouldBeExcludedWhenFindDefinition() {
        val definition1 = TestDefinition("A") { false }
        val definition2 = TestDefinition("B") { true }
        val definition3 = TestDefinition("C") { false }
        definitionsSourcesPoint.addDefinitionWithSource(definition1, definition2, definition3)
        val testSource = TestSource("any.kts")

        assertEquals("B", provider.findDefinition(testSource)?.definitionId)
    }

    fun testDisabledWithSettingsDefinitionsShouldBeExcludedWhenFindDefinition() {
        val definition1 = TestDefinition("A")
        val definition2 = TestDefinition("B")
        val definition3 = TestDefinition("C")
        definitionsSourcesPoint.addDefinitionWithSource(definition1, definition2, definition3)

        settings.setSettings(
            listOf(
                ScriptDefinitionSetting(name = "Any", definitionId = "A", enabled = false),
                ScriptDefinitionSetting(name = "Any", definitionId = "C", enabled = false)
            )
        )

        val testSource = TestSource("any.kts")
        assertEquals("B", provider.findDefinition(testSource)?.definitionId)
    }

    fun testOrderSettingsShouldBeRespectedWhenFindDefinition() {
        val definition1 = TestDefinition("A")
        val definition2 = TestDefinition("B")
        val definition3 = TestDefinition("C")
        definitionsSourcesPoint.addDefinitionWithSource(definition1, definition2, definition3)

        settings.setSettings(
            listOf(
                ScriptDefinitionSetting(name = "Any", definitionId = "B", enabled = true),
                ScriptDefinitionSetting(name = "Any", definitionId = "A", enabled = true),
                ScriptDefinitionSetting(name = "Any", definitionId = "C", enabled = true)
            )
        )

        val testSource = TestSource("any.kts")
        assertEquals("B", provider.findDefinition(testSource)?.definitionId)
    }

    private class TestSource(override val locationId: String?) : SourceCode {
        override val text: String get() = ""
        override val name: String? get() = locationId
    }

    private class TestDefinition(
        id: String,
        override val fileExtension: String = ".kts",
        private val isScriptCondition: (SourceCode) -> Boolean = { true }
    ) : ScriptDefinition.FromConfigurations(
        defaultJvmScriptingHostConfiguration,
        ScriptCompilationConfiguration.Default,
        ScriptEvaluationConfiguration.Default
    ) {
        override val definitionId: String = id
        override fun isScript(script: SourceCode): Boolean = isScriptCondition.invoke(script)
    }
}
