// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.definitions

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.configurations.KotlinScriptService
import org.jetbrains.kotlin.idea.core.script.definition.loadDefinitionsFromTemplates
import org.jetbrains.kotlin.idea.core.script.getVirtualFile
import org.jetbrains.kotlin.idea.core.script.loggingReporter
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.scriptingErrorLog
import org.jetbrains.kotlin.idea.core.script.scriptingInfoLog
import org.jetbrains.kotlin.idea.core.script.settings.KotlinScriptingSettings
import org.jetbrains.kotlin.idea.core.script.settings.parsedClassNames
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsFromClasspathDiscoverySource
import java.io.File
import java.nio.file.Path
import kotlin.script.experimental.api.IdeScriptCompilationConfigurationKeys
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptEvaluationConfiguration
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.baseClass
import kotlin.script.experimental.api.hostConfiguration
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.with
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider
import kotlin.script.experimental.jvm.defaultJvmScriptingHostConfiguration
import kotlin.script.experimental.util.PropertiesCollection
import kotlin.time.Duration.Companion.milliseconds


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
class ScriptDefinitionsModificationTracker(private val project: Project) : SimpleModificationTracker() {
    /** A script file can get another definition, so its editor toolbar is built again. */
    override fun incModificationCount() {
        super.incModificationCount()
        EditorNotifications.getInstance(project).updateAllNotifications()
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): ScriptDefinitionsModificationTracker = project.service()
    }
}

class ScriptDefinitionProviderImpl(val project: Project, private val scope: CoroutineScope) : ScriptDefinitionProvider {
    override val currentDefinitions: Sequence<ScriptDefinition>
        get() = cachedProvidedDefinitions.enabledInOrder()

    private class Loaded(val definitions: List<ScriptDefinition>?, val request: Long)

    private val requests = MutableStateFlow(1L)
    private val loaded = MutableStateFlow(Loaded(null, 0))

    private val bundledDefinition: ScriptDefinition by lazy { project.defaultDefinition }

    init {
        project.extensionArea.getExtensionPoint(ScriptDefinitionsProvider.EP_NAME).addChangeListener(scope) { scheduleLoading() }
        // A provider loads the templates of the project only while the project is trusted.
        val connection = ApplicationManager.getApplication().messageBus.connect(scope)
        connection.subscribe(TrustedProjectsListener.TOPIC, object : TrustedProjectsListener {
            override fun onProjectTrusted(project: Project) {
                if (project == this@ScriptDefinitionProviderImpl.project) scheduleLoading()
            }

            override fun onProjectUntrusted(project: Project) {
                if (project == this@ScriptDefinitionProviderImpl.project) scheduleLoading()
            }
        })
        // The StateFlow merges the requests, and the retry repeats a load that a disposed module or a cancellation stopped.
        // The pause keeps an error that repeats from a busy loop.
        scope.launch {
            requests
                .onEach(::load)
                .retryWhen { _, _ ->
                    delay(100.milliseconds)
                    currentCoroutineContext().isActive
                }
                .collect()
        }
    }

    /** The definitions of the last load. A lookup never reads the disk, and before the first load it gets only the bundled definition. */
    val cachedProvidedDefinitions: List<ScriptDefinition>
        get() = loaded.value.definitions ?: listOf(bundledDefinition)

    fun scheduleLoading() {
        requests.update { it + 1 }
    }

    /** Waits for the loads that were asked for before this call. */
    suspend fun awaitLoading() {
        val request = requests.value
        loaded.first { it.request >= request }
    }

    private fun load(request: Long) {
        val previous = loaded.value.definitions
        val definitions = try {
            // The fingerprints read the disk here, so that a lookup gets them from memory.
            loadFromProviders().onEach { it.templateFingerprint }
        } catch (e: Throwable) {
            rethrowControlFlowException(e)
            scriptingErrorLog("cannot load script definitions", e)
            previous
        }
        KotlinScriptService.getInstance(project).scheduleReloadOutdatedOpenScripts()
        // Without a script entity, a script that a lookup saw before this load drops the caches when it gets its entity.
        if (previous == null && project.workspaceModelHasAnyScript()) {
            scope.launch(ModalityState.nonModal().asContextElement()) {
                edtWriteAction { KotlinScriptService.dropKotlinScriptCaches(project) }
            }
        }
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
        // The last step, because it also wakes the waiters.
        loaded.value = Loaded(definitions, request)
    }

    private fun loadFromProviders(): List<ScriptDefinition> =
        project.extensionArea.getExtensionPoint(ScriptDefinitionsProvider.EP_NAME).extensionList.asSequence().flatMap { provider ->
            try {
                scriptingInfoLog("processing definitions provider ${provider::class.java.name}")
                val baseHostConfiguration = defaultJvmScriptingHostConfiguration
                // TODO: rewrite load and discovery to return kotlin.script.experimental.host.ScriptDefinition to avoid unnecessary conversions
                val templateDefinitions = scriptDefinitionsFromClasspath(
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

                provider.provideDefinitions(baseHostConfiguration, templateDefinitions).asSequence()
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
                    }.toList()
            } catch (e: Throwable) {
                rethrowControlFlowException(e)
                scriptingErrorLog("cannot load definitions of provider ${provider::class.java.name}", e)
                emptyList()
            }
        }.toList() + bundledDefinition

    override fun isScript(script: SourceCode): Boolean = findDefinition(script) != null

    override fun getKnownFilenameExtensions(): Sequence<String> = currentDefinitions.map { it.fileExtension }.distinct()

    /**
     * The definition that the configuration of the script comes from, while the script has an entity.
     * Without an entity, the definition that matches the file now.
     */
    override fun findDefinition(script: SourceCode): ScriptDefinition? {
        val definitions = cachedProvidedDefinitions
        val matching = definitions.findFor(script) ?: return null
        val definitionId = getVirtualFile(script)?.let { KotlinScriptEntityProvider.provide(project, it) }?.definitionId ?: return matching
        return definitions.firstOrNull { it.definitionId == definitionId } ?: matching
    }

    /** The definition that matches the file now, whatever definition its entity comes from. */
    internal fun findMatchingDefinition(script: SourceCode): ScriptDefinition = cachedProvidedDefinitions.findFor(script) ?: bundledDefinition

    /** True after the first load. Before it, a lookup gets only the bundled definition. */
    val isLoaded: Boolean
        get() = loaded.value.definitions != null

    private fun List<ScriptDefinition>.enabledInOrder(): Sequence<ScriptDefinition> {
        val settings = KotlinScriptingSettings.getInstance(project)
        return asSequence().filter { settings.isScriptDefinitionEnabled(it) }.sortedBy { settings.getScriptDefinitionOrder(it) }
    }

    private fun List<ScriptDefinition>.findFor(script: SourceCode): ScriptDefinition? {
        val locationId = script.locationId ?: return null
        if (nonScriptFilenameSuffixes.any { locationId.endsWith(it, ignoreCase = true) }) return null

        return enabledInOrder().firstOrNull { it.isScript(script) }
    }

    override fun getDefaultDefinition(): ScriptDefinition = bundledDefinition

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

/** In a project with scripts, the first load starts at the project open and not at the first lookup of a definition. */
internal class ScriptDefinitionsStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        if (project.workspaceModelHasAnyScript()) {
            ScriptDefinitionProviderImpl.getInstance(project)
        }
    }
}

private fun Project.workspaceModelHasAnyScript(): Boolean = workspaceModel.currentSnapshot.entities(KotlinScriptEntity::class.java).any()
