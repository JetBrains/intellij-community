// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.openapi.components.service
import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import java.io.File
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.filePathPattern
import kotlin.script.experimental.host.FileScriptSource
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

class ScriptDefinitionsProviderBridgeTest : KotlinLightCodeInsightFixtureTestCase() {

    fun `test definitions from the provider EP reach the definition provider`() {
        registerProvidedDefinition()

        val extensions = ScriptDefinitionProviderImpl.getInstance(project).currentDefinitions.map { it.fileExtension }.toList()
        assertContainsElements(extensions, PROVIDED_EXTENSION)
    }

    fun `test definitions from the provider EP take precedence over the bundled default`() {
        registerProvidedDefinition()

        val definitions = ScriptDefinitionProviderImpl.getInstance(project).currentDefinitions.toList()
        val provided = definitions.indexOfFirst { it.fileExtension == PROVIDED_EXTENSION }
        val bundled = definitions.indexOfFirst { it.isDefault }

        assertTrue("The definition bridged from the provider EP must be present", provided >= 0)
        assertTrue("The bundled default definition must be present and sort last", bundled >= 0)
        assertTrue(
            "Definitions bridged from the provider EP must keep order = Int.MIN_VALUE and sort before the bundled default",
            provided < bundled,
        )
    }

    fun `test filePathPattern narrows the extension based match`() {
        val filtered = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = {
                fileExtension(FILTERED_EXTENSION)
                filePathPattern(".*/accepted/.*")
            },
        )
        registerProvider(filtered)

        val definitionProvider = project.service<ScriptDefinitionProvider>()
        assertEquals(
            "A file outside filePathPattern must fall through to the bundled default definition",
            true,
            definitionProvider.findDefinition(FileScriptSource(File("/rejected/script.$FILTERED_EXTENSION")))?.isDefault,
        )
        assertEquals(
            "A file inside filePathPattern must be claimed by the filtered definition",
            false,
            definitionProvider.findDefinition(FileScriptSource(File("/accepted/script.$FILTERED_EXTENSION")))?.isDefault,
        )
    }

    private fun registerProvidedDefinition() = registerProvider(
        createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = { fileExtension(PROVIDED_EXTENSION) },
        )
    )

    private fun registerProvider(definition: ScriptDefinition) {
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "ScriptDefinitionsProviderBridgeTest"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }

    companion object {
        private const val PROVIDED_EXTENSION = "provided.kts"
        private const val FILTERED_EXTENSION = "filtered.kts"
    }
}
