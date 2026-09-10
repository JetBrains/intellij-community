// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.definitions

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SimpleModificationTracker
import org.jetbrains.kotlin.caches.project.cacheByClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.v1.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.script.shared.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.v1.loggingReporter
import org.jetbrains.kotlin.idea.core.script.v1.scriptingInfoLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration


internal val SCRIPT_DEFINITIONS_PROVIDER_ID: Key<String> = Key.create("kotlin.script.definitions.provider.id")

@Service(Service.Level.PROJECT)
class ScriptDefinitionsModificationTracker : SimpleModificationTracker() {
    companion object {
        @JvmStatic
        fun getInstance(project: Project): ScriptDefinitionsModificationTracker = project.service()
    }
}

class ScriptDefinitionProviderImpl(val project: Project) : ScriptDefinitionProvider {
    override val currentDefinitions: Sequence<ScriptDefinition>
        get() {
            val settingsProvider = KotlinScriptingSettings.getInstance(project)

            return cachedProvidedDefinitions.asSequence()
                .filter { settingsProvider.isScriptDefinitionEnabled(it) }
                .sortedBy { settingsProvider.getScriptDefinitionOrder(it) }
        }

    val cachedProvidedDefinitions: List<ScriptDefinition>
        get() = project.cacheByClass(
            ScriptDefinitionProviderImpl::class.java,
            ScriptDefinitionsModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        ) {
            project.extensionArea
                .getExtensionPoint(ScriptDefinitionsProvider.EP_NAME)
                .extensionList.asSequence().flatMap { provider ->
                    scriptingInfoLog("processing definitions provider ${provider::class.java.name}")
                    val baseHostConfiguration = defaultJvmScriptingHostConfiguration
                    // TODO: rewrite load and discovery to return kotlin.script.experimental.host.ScriptDefinition to avoid unnecessary conversions
                    val loadedDefinitions = scriptDefinitionsFromClasspath(
                        classpath = provider.getTemplateClasspath().toList(),
                        templateFqns = provider.getDefinitionClasses().toList(),
                        discover = provider.useDiscovery(),
                        hostConfiguration = baseHostConfiguration,
                    ).map {
                        kotlin.script.experimental.host.ScriptDefinition(
                            it.compilationConfiguration,
                            it.evaluationConfiguration ?: ScriptEvaluationConfiguration.Default,
                        )
                    }.toList()

                    provider.provideDefinitions(baseHostConfiguration, loadedDefinitions).asSequence().map {
                        IdeScriptDefinitionFromProvider(baseHostConfiguration, it).apply {
                            putUserData(SCRIPT_DEFINITIONS_PROVIDER_ID, provider.id)
                        }
                    }
                }.toList() + project.defaultDefinition
        }

    override fun isScript(script: SourceCode): Boolean = findDefinition(script) != null

    override fun getKnownFilenameExtensions(): Sequence<String> =
        currentDefinitions.map { it.fileExtension }.distinct()

    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null
        if (nonScriptFilenameSuffixes.any { locationId.endsWith(it, ignoreCase = true) }) return null

        return currentDefinitions.firstOrNull { it.isScript(script) }
    }

    override fun getDefaultDefinition(): ScriptDefinition = project.defaultDefinition

    companion object {
        private val nonScriptFilenameSuffixes: Set<String> = setOf(".${KotlinFileType.EXTENSION}", ".${JavaFileType.DEFAULT_EXTENSION}")

        fun getInstance(project: Project): ScriptDefinitionProviderImpl =
            project.service<ScriptDefinitionProvider>() as ScriptDefinitionProviderImpl
    }

    private fun scriptDefinitionsFromClasspath(
        classpath: List<Path>,
        templateFqns: List<String> = emptyList(),
        discover: Boolean = templateFqns.isEmpty(),
        hostConfiguration: ScriptingHostConfiguration = defaultJvmScriptingHostConfiguration,
    ): Sequence<ScriptDefinition> {
        val fromTemplates =
            if (templateFqns.isEmpty()) emptySequence()
            else loadDefinitionsFromTemplates(templateFqns, classpath, baseHostConfiguration = hostConfiguration)

        val discovered =
            if (!discover) emptySequence()
            else ScriptDefinitionsFromClasspathDiscoverySource(
                classpath.map { File(it.toString()) },
                hostConfiguration,
                ::loggingReporter,
            ).definitions

        return fromTemplates + discovered
    }
}

private class IdeScriptDefinitionFromProvider(
    baseHostConfiguration: ScriptingHostConfiguration,
    definition: kotlin.script.experimental.host.ScriptDefinition,
) : ScriptDefinition.FromNewDefinition(baseHostConfiguration, definition) {
    init {
        order = Int.MIN_VALUE
    }
}