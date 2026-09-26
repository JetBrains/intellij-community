// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.kotlin.caches.project.cacheByClass
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.script.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.loggingReporter
import org.jetbrains.kotlin.idea.core.script.scriptingInfoLog
import org.jetbrains.kotlin.idea.core.script.settings.parsedClassNames
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.with
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.util.PropertiesCollection


/**
 * The id of the `ScriptDefinitionsProvider` extension that supplied the definition.
 *
 * A configuration key for the same reason as [discoverySource]: a definition rebuilt outside the
 * cache still answers it. A reader that got null would report `other` and quietly undercount a
 * bundled provider, because a missing id is not an error.
 *
 * It stays a key of its own, because it is not the discovery source. A definition from a marker file
 * reports the `FROM_DEPENDENCIES` provider and the `MarkerFile` source at the same time.
 */
internal val IdeScriptCompilationConfigurationKeys.providerId: PropertiesCollection.Key<String> by PropertiesCollection.key()

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

            return cachedProvidedDefinitions.asSequence().filter { settingsProvider.isScriptDefinitionEnabled(it) }
                .sortedBy { settingsProvider.getScriptDefinitionOrder(it) }
        }

    val cachedProvidedDefinitions: List<ScriptDefinition>
        get() = project.cacheByClass(
            ScriptDefinitionProviderImpl::class.java,
            ScriptDefinitionsModificationTracker.getInstance(project),
            ProjectRootModificationTracker.getInstance(project),
        ) {
            project.extensionArea.getExtensionPoint(ScriptDefinitionsProvider.EP_NAME).extensionList.asSequence().flatMap { provider ->
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

                    provider.provideDefinitions(baseHostConfiguration, loadedDefinitions).asSequence()
                        .map { (compilationConfiguration, evaluationConfiguration) ->
                            val id = compilationConfiguration[ScriptCompilationConfiguration.baseClass]!!.typeName
                            val compilationConfigurationWithIdeDetails = compilationConfiguration.with {
                                ide {
                                    discoverySource(provider.toScriptDiscoverySource(id))
                                    providerId(provider.id)
                                }
                            }

                            val host = compilationConfigurationWithIdeDetails[ScriptCompilationConfiguration.hostConfiguration]
                                ?: baseHostConfiguration

                            ScriptDefinition.FromConfigurations(
                                host, compilationConfigurationWithIdeDetails, evaluationConfiguration
                            ).apply {
                                order = Int.MIN_VALUE
                            }
                        }
                }.toList() + project.defaultDefinition
        }

    override fun isScript(script: SourceCode): Boolean = findDefinition(script) != null

    override fun getKnownFilenameExtensions(): Sequence<String> = currentDefinitions.map { it.fileExtension }.distinct()

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
        val fromTemplates = if (templateFqns.isEmpty()) emptySequence()
        else loadDefinitionsFromTemplates(templateFqns, classpath, baseHostConfiguration = hostConfiguration)

        val discovered = if (!discover) emptySequence()
        else ScriptDefinitionsFromClasspathDiscoverySource(
            classpath.map { File(it.toString()) },
            hostConfiguration,
            ::loggingReporter,
        ).definitions

        return fromTemplates + discovered
    }
}

/**
 * A marker file is the most specific origin, so it wins over the settings list and over the provider.
 * An FQN that the settings list and no marker declares is manual. Everything else names the extension
 * class that supplied it, which is the only origin that identifies a definition exactly.
 */
private fun ScriptDefinitionsProvider.toScriptDiscoverySource(definitionId: String): ScriptDefinitionDiscoverySource {
    val fromDependencies = this as? DefinitionFromDependenciesProvider
    if (fromDependencies != null) {
        fromDependencies.markerOriginFor(definitionId)?.let {
            val location = it.jarPath?.let { path -> relativeToProject(fromDependencies.project, path) }
            return ScriptDefinitionDiscoverySource.MarkerFile(it.rootName, it.jarPath, location)
        }
        if (definitionId in KotlinScriptingSettings.getInstance(fromDependencies.project).state.parsedClassNames) {
            return ScriptDefinitionDiscoverySource.Manual
        }
    }

    val plugin = PluginManager.getPluginByClass(javaClass)
    return ScriptDefinitionDiscoverySource.Provider(javaClass.name, plugin?.name, plugin?.pluginId?.idString)
}

/**
 * A path inside the project reads better relative to it. Anything outside keeps its full path.
 *
 * The platform helper matches the case rules of the file system, which `Path.relativize` does not.
 * It compares the two strings, so both must carry the same separator.
 */
private fun relativeToProject(project: Project, path: String): String {
    val base = project.basePath ?: return path
    val relative = FileUtil.getRelativePath(base, FileUtil.toSystemIndependentName(path), '/') ?: return path
    return if (relative.startsWith("..")) path else relative
}
