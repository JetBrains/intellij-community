// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.testFramework.registerExtension
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.ScriptAcceptedLocation
import kotlin.script.experimental.api.acceptedLocations
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs

private const val PROVIDER_ID: String = "ScriptDefinitionDiscoverySourceTest"

class ScriptDefinitionDiscoverySourceTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    fun `test a definition from an extension point reports the provider id`() {
        registerProvider(definitionFor("provided.kts"))

        val source = sourceOfDefinitionWith("provided.kts")

        assertInstanceOf(source, ScriptDefinitionDiscoverySource.Provider::class.java)
        assertTrue(
            "Expected the fake provider implementation class, got $source",
            (source as ScriptDefinitionDiscoverySource.Provider).implementationFqn.startsWith(ScriptDefinitionDiscoverySourceTest::class.java.name),
        )
    }

    fun `test the bundled default definition reports its implementation class`() {
        val default = ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions.last()
        val source = default.compilationConfiguration[ScriptCompilationConfiguration.ide.discoverySource]

        assertInstanceOf(source, ScriptDefinitionDiscoverySource.Provider::class.java)
        assertTrue(
            "Expected the bundled definition class, got $source",
            (source as ScriptDefinitionDiscoverySource.Provider).implementationFqn.endsWith("BundledScriptDefinition"),
        )
    }

    fun `test a definition built outside the cache still reports its source`() {
        // `defaultDefinition` builds a new object on every read, so this is not the instance the cache
        // holds. A configuration key travels with it; user data on the cached instance would not.
        val fresh = ScriptDefinitionProviderImpl.getInstance(project).getDefaultDefinition()
        val cached = ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions.last()

        assertNotSame("The test is pointless if both reads return one object", fresh, cached)
        assertInstanceOf(fresh.compilationConfiguration[ScriptCompilationConfiguration.ide.discoverySource], ScriptDefinitionDiscoverySource.Provider::class.java)
    }

    fun `test the provider id travels in the configuration`() {
        registerProvider(definitionFor("reported.kts"))

        val definition = ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions
            .first { it.fileExtension == "reported.kts" }

        // On the configuration and not on the instance, so a rebuilt definition still reports it
        // instead of falling back to the `other` bucket.
        assertEquals(PROVIDER_ID, definition.compilationConfiguration[ScriptCompilationConfiguration.ide.providerId])
    }

    fun `test the source stays attached after the definition cache is invalidated`() {
        registerProvider(definitionFor("reloaded.kts"))
        assertNotNull("The source must be attached on the first read", sourceOfDefinitionWith("reloaded.kts"))

        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()

        assertInstanceOf(sourceOfDefinitionWith("reloaded.kts"), ScriptDefinitionDiscoverySource.Provider::class.java)
    }

    private fun sourceOfDefinitionWith(extension: String): ScriptDefinitionDiscoverySource? =
        ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions
            .first { it.fileExtension == extension }
            .compilationConfiguration[ScriptCompilationConfiguration.ide.discoverySource]

    private fun definitionFor(extension: String): ScriptDefinition = createScriptDefinitionFromTemplate(
        KotlinType(ScriptTemplateWithArgs::class),
        defaultJvmScriptingHostConfiguration,
        compilation = {
            fileExtension(extension)
            ide { acceptedLocations(ScriptAcceptedLocation.Everywhere) }
        },
    )

    private fun registerProvider(definition: ScriptDefinition) {
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = PROVIDER_ID
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = listOf(definition)
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
    }
}
