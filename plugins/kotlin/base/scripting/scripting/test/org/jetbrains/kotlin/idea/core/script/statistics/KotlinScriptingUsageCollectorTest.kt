// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.statistics

import com.intellij.internal.statistic.FUCollectorTestCase
import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.testFramework.registerExtension
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.definitions.KotlinScriptDefinitionsProviderId
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import kotlin.script.experimental.api.KotlinType
import kotlin.script.experimental.api.fileExtension
import kotlin.script.experimental.host.ScriptDefinition
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.createScriptDefinitionFromTemplate
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.templates.standard.ScriptTemplateWithArgs
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition as IdeScriptDefinition

class KotlinScriptingUsageCollectorTest : KotlinLightCodeInsightFixtureTestCase() {
    override fun runInDispatchThread(): Boolean = false

    override fun tearDown() {
        try {
            KotlinScriptingSettings.getInstance(project).update {
                it.copy(settings = emptyList())
            }
        } finally {
            super.tearDown()
        }
    }

    fun `test definition counts sum to all provided definitions and report disabled ones`() {
        val definitions = ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions
        val disabled = definitions.first()

        KotlinScriptingSettings.getInstance(project).update {
            it.copy(settings = listOf(KotlinScriptingSettings.DefinitionSetting(disabled.name, disabled.definitionId, enabled = false)))
        }

        val metrics = collect().filter { it.eventId == "definitions.count" }.map { it.data.build() }
        assertEquals(definitions.size, metrics.sumOf { it["count"] as Int })
        assertEquals(1, metrics.sumOf { it["disabled_count"] as Int })
    }

    fun `test every bundled provider reports its definition count including zero`() {
        val reported = definitionCountsByProvider()

        for (providerId in KotlinScriptDefinitionsProviderId.entries) {
            assertTrue(
                "Provider ${providerId.id} must be reported even with no definitions, got $reported",
                reported.containsKey(providerId.id),
            )
        }
        assertEquals("The bundled default definition must be reported, got $reported", 1, reported["BundledDefault"])
    }

    fun `test definitions of a provider outside the Kotlin plugin are reported as other`() {
        registerProvider()

        assertEquals(1, definitionCountsByProvider()["other"])
    }

    fun `test external providers count is reported as rounded value`() {
        val externalProvidersCount = ScriptDefinitionsProvider.EP_NAME.getExtensions(project)
            .count { getPluginInfo(it.javaClass).id != KotlinIdePlugin.id.idString }

        val metrics = collect().filter { it.eventId == "external.providers.count" }.map { it.data.build() }
        if (externalProvidersCount > 0) {
            val expected = com.intellij.internal.statistic.utils.StatisticsUtil.roundToPowerOfTwo(externalProvidersCount)
            assertEquals(expected, metrics.single()["count"])
        }
    }

    fun `test definition counts and disabled counts are rounded to power of two`() {
        val definitions = (1..3).map { index ->
            createScriptDefinitionFromTemplate(
                KotlinType(ScriptTemplateWithArgs::class),
                defaultJvmScriptingHostConfiguration,
                compilation = { fileExtension("ext$index.kts") },
            )
        }
        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "KotlinScriptingUsageCollectorTestMulti"
                override fun provideDefinitions(
                    baseHostConfiguration: ScriptingHostConfiguration,
                    loadedScriptDefinitions: List<ScriptDefinition>,
                ): Iterable<ScriptDefinition> = definitions
            },
            testRootDisposable,
        )
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()

        val provided = ScriptDefinitionProviderImpl.getInstance(project).cachedProvidedDefinitions.filter { it.reportedProviderId() == "other" }
        KotlinScriptingSettings.getInstance(project).update {
            it.copy(
                settings = provided.map { def ->
                    KotlinScriptingSettings.DefinitionSetting(def.name, def.definitionId, enabled = false)
                }
            )
        }

        val otherMetrics = collect().filter { it.eventId == "definitions.count" && it.data.build()["provider_id"] == "other" }.map { it.data.build() }
        assertEquals(4, otherMetrics.single()["count"])
        assertEquals(4, otherMetrics.single()["disabled_count"])
    }

    fun `test loaded scripts are attributed to the provider that claimed them`() {
        registerProvider()

        val bundled = myFixture.configureByText("plain.kts", "val x = 1").virtualFile
        val provided = myFixture.configureByText("custom.$PROVIDED_EXTENSION", "val y = 2").virtualFile
        runBlocking {
            KotlinScriptService.getInstance(project).load(bundled)
            KotlinScriptService.getInstance(project).load(provided)
        }

        val reported = collect()
            .filter { it.eventId == "scripts.count" }
            .associate { it.data.build()["provider_id"] to it.data.build()["count"] }

        assertEquals("Plain .kts must fall back to the bundled definition, got $reported", 1, reported["BundledDefault"])
        assertEquals("Script of a third-party definition must be reported as other, got $reported", 1, reported["other"])
    }

    fun `test every provider shipped by the Kotlin plugin has a known id`() {
        val knownIds = KotlinScriptDefinitionsProviderId.entries.map { it.id }

        ScriptDefinitionsProvider.EP_NAME.getExtensions(project)
            .filter { getPluginInfo(it.javaClass).id == KotlinIdePlugin.id.idString }
            .forEach {
                assertTrue(
                    "Provider ${it::class.java.name} reports id '${it.id}', which is not in $knownIds, " +
                            "so its definitions and scripts would be reported as 'other'",
                    it.id in knownIds,
                )
            }
    }

    private fun definitionCountsByProvider(): Map<Any?, Any?> = collect()
        .filter { it.eventId == "definitions.count" }
        .associate { it.data.build()["provider_id"] to it.data.build()["count"] }

    private fun collect(): Set<MetricEvent> =
        FUCollectorTestCase.collectProjectStateCollectorEvents(KotlinScriptingUsageCollector::class.java, project)

    private fun registerProvider() {
        val definition = createScriptDefinitionFromTemplate(
            KotlinType(ScriptTemplateWithArgs::class),
            defaultJvmScriptingHostConfiguration,
            compilation = { fileExtension(PROVIDED_EXTENSION) },
        )

        project.registerExtension(
            ScriptDefinitionsProvider.EP_NAME,
            object : ScriptDefinitionsProvider {
                override val id: String = "KotlinScriptingUsageCollectorTest"
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
    }
}
