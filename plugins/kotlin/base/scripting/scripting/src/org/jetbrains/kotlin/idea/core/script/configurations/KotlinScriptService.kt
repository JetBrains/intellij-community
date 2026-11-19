// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.kotlin.idea.core.script.configurations

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.ide.TrustedFiles
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithoutContent
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.findEntitiesByVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.asNio
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.psi.PsiManager
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalScriptModuleStateModificationEvent
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerPluginsScriptConfigurationListener
import org.jetbrains.kotlin.idea.core.script.definitions.DefinitionFromDependenciesProvider
import org.jetbrains.kotlin.idea.core.script.definitions.ScriptDefinitionProviderImpl
import org.jetbrains.kotlin.idea.core.script.definitions.templateFingerprint
import org.jetbrains.kotlin.idea.core.script.dependencies.ScriptArtifactResolutionService
import org.jetbrains.kotlin.idea.core.script.dependencies.toRequest
import org.jetbrains.kotlin.idea.core.script.getOrCreateScriptConfigurationId
import org.jetbrains.kotlin.idea.core.script.getVirtualFile
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.modules.KotlinScriptLibraryEntityId
import org.jetbrains.kotlin.idea.core.script.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.definition.BundledScriptDefinition
import org.jetbrains.kotlin.idea.core.script.definition.javaHomePath
import org.jetbrains.kotlin.idea.core.script.definition.jdkSupplier
import org.jetbrains.kotlin.idea.core.script.definition.moduleSupplier
import org.jetbrains.kotlin.idea.core.script.RELOAD_NEEDED
import org.jetbrains.kotlin.idea.core.script.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.awaitExternalSystemInitialization
import org.jetbrains.kotlin.idea.core.script.scriptingDebugLog
import org.jetbrains.kotlin.idea.core.script.scriptingErrorLog
import org.jetbrains.kotlin.idea.core.script.scriptingWarnLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.getScriptCollectedData
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import java.io.File
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.script.experimental.api.DependencyCoordinates
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCollectedData
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.SourceCode
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.dependencyRepositories
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvm.updateClasspath

@Service(Service.Level.PROJECT)
class KotlinScriptService(val project: Project, val coroutineScope: CoroutineScope) {

    fun scheduleLoading(virtualFile: VirtualFile) {
        coroutineScope.launchTracked {
            project.awaitExternalSystemInitialization()
            if (virtualFile.name.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION)) {
                load(virtualFile)
            }
        }
    }

    /** Waits for the pending loads of the script definitions first, so that the entity gets the current definition. */
    suspend fun load(virtualFile: VirtualFile) {
        if (virtualFile is VirtualFileWithoutContent) return
        if (!KotlinScriptProcessingFilter.shouldProcessScript(project, virtualFile)) return
        assert(!application.isWriteAccessAllowed)
        ScriptDefinitionProviderImpl.getInstance(project).awaitLoading()
        if (hasOutdatedDefinition(virtualFile)) invalidate(virtualFile)
        update(virtualFile, virtualFile.findScriptDefinition())
    }

    /** Waits for the pending loads of the script definitions first, so that the script keeps its entity during them. */
    suspend fun reload(virtualFile: VirtualFile) {
        ScriptDefinitionProviderImpl.getInstance(project).awaitLoading()
        invalidate(virtualFile)
        load(virtualFile)
    }

    fun scheduleLoadOpenScripts(): Job = coroutineScope.launchTracked {
        for (openFile in collectOpenScriptFiles(project)) {
            load(openFile)
        }
    }

    /**
     * A closed script reloads when it opens.
     *
     * The returned job waits, but does not rethrow a failure.
     */
    fun scheduleReloadOutdatedOpenScripts(): Job = coroutineScope.launchTracked {
        ScriptDefinitionProviderImpl.getInstance(project).awaitLoading()
        for (script in collectOpenScriptFiles(project)) {
            if (hasOutdatedDefinition(script)) reload(script)
        }
    }

    /**
     * Another fingerprint means that a build rewrote the template.
     * A script of another owner, such as a Gradle sync, is never outdated here, because its owner updates it.
     */
    internal fun hasOutdatedDefinition(virtualFile: VirtualFile): Boolean {
        val entity = KotlinScriptEntityProvider.findKotlinScriptEntity(project, virtualFile)
            ?.takeIf { it.entitySource == KotlinScriptEntitySource } ?: return false
        val definition = ScriptDefinitionProviderImpl.getInstance(project).findMatchingDefinition(VirtualFileScriptSource(virtualFile))
        return entity.definitionId != definition.definitionId || entity.definitionFingerprint != definition.templateFingerprint
    }

    private class CircularScriptException(override val message: String) : Throwable(message)
    private val notificationGroup: NotificationGroup
        get() = NotificationGroupManager.getInstance().getNotificationGroup("KotlinScriptNotificationGroup")

    private suspend fun update(virtualFile: VirtualFile, definition: ScriptDefinition) {
        if (definition !is BundledScriptDefinition && !TrustedFiles.isTrusted(virtualFile, project)) return
        if (KotlinScriptEntityProvider.provide(project, virtualFile) != null) return

        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val scriptUrl = virtualFile.toVirtualFileUrl(urlManager)

        val (rootConfiguration, importedConfigurations) = try {
            val rootConfiguration = resolveConfiguration(virtualFile, definition)
            val importedConfigurations = topologicalSort(nodes = listOf(rootConfiguration), reportCycle = {
                throw CircularScriptException(
                    KotlinBaseScriptingBundle.message(
                        "script.part.circular.file.import.chain", it.valueOrNull()?.script?.name ?: "<null>"
                    )
                )
            }) {
                this.valueOrNull()?.importedScripts.orEmpty().map {
                    resolveConfiguration(it, definition)
                }
            }.reversed()

            rootConfiguration to importedConfigurations
        } catch (e: Throwable) {
            scriptingWarnLog("Script processing failed", e)
            when (e) {
                is CancellationException -> currentCoroutineContext().ensureActive()
                is CircularScriptException -> notificationGroup.createNotification(
                    KotlinBaseScriptingBundle.message("circular.script.import"),
                    e.message,
                    NotificationType.ERROR,
                ).notify(project)
                else -> notificationGroup.createNotification(
                    KotlinBaseScriptingBundle.message("script.configuration.loading.failed", virtualFile.name),
                    e.message ?: KotlinBaseScriptingBundle.message("script.configuration.failed.unknown", virtualFile.name),
                    NotificationType.ERROR,
                ).notify(project)
            }
            return
        }

        val refinedConfiguration = when (rootConfiguration) {
            is ResultWithDiagnostics.Success<ScriptCompilationConfigurationWrapper> -> rootConfiguration.value.configuration
            is ResultWithDiagnostics.Failure -> {
                rootConfiguration.reports.forEach {
                    notificationGroup.createNotification(
                        KotlinBaseScriptingBundle.message("script.configuration.failed", virtualFile.name),
                        it.message,
                        when (it.severity) {
                            ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> NotificationType.ERROR
                            ScriptDiagnostic.Severity.WARNING -> NotificationType.WARNING
                            else -> NotificationType.ERROR
                        },
                    ).notify(project)
                }
                null
            }
        } ?: return

        val configuration = refinedConfiguration.resolveDependencies(project, virtualFile)
        configuration.refreshVfsDependencies()
        val relatedModuleIds = readAction {
            listOfNotNull(definition.compilationConfiguration[ScriptCompilationConfiguration.ide.moduleSupplier]?.invoke(project, virtualFile))
        }

        val entitySource = KotlinScriptEntityProvider.entitySource(project, virtualFile) ?: KotlinScriptEntitySource
        project.workspaceModel.update("updating kotlin script entities [$entitySource]") { storage ->
            if (!storage.containsScriptEntity(scriptUrl)) {
                val libraries = generateScriptLibraryEntities(project, configuration, definition).toList()
                val libraryIds = mutableListOf<KotlinScriptLibraryEntityId>()
                for ((scope, classes, sources) in libraries) {
                    val libraryId = KotlinScriptLibraryEntityId(scope, classes)
                    libraryIds += libraryId

                    val existingLibrary = storage.resolve(libraryId)
                    if (existingLibrary == null) {
                        storage addEntity KotlinScriptLibraryEntity(
                            scope,
                            classes,
                            setOf(scriptUrl),
                            entitySource
                        ) {
                            this.sources += sources
                        }
                    } else {
                        storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                            this.sources += sources
                            this.usedInScripts += scriptUrl
                        }
                    }
                }
                storage addEntity KotlinScriptEntity(scriptUrl, libraryIds, definition.definitionId, definition.templateFingerprint, entitySource) {
                    this.configurationId = configuration.getOrCreateScriptConfigurationId(storage, entitySource)
                    this.sdkId = configuration.sdkId
                    this.relatedModuleIds = relatedModuleIds.toMutableList()
                }
            }
        }

        for (importedConfig in importedConfigurations) {
            val script = importedConfig.valueOrNull()?.script ?: continue
            val importedFile = getVirtualFile(script) ?: continue
            update(importedFile, importedFile.findScriptDefinition())
        }
    }

    // A dependency JAR can be downloaded again after the last VFS scan.
    private fun ScriptCompilationConfiguration.refreshVfsDependencies() {
        ThreadingAssertions.assertNoReadAccess()

        get(ScriptCompilationConfiguration.dependencies).orEmpty()
            .plus(get(ScriptCompilationConfiguration.ide.dependenciesSources).orEmpty())
            .filterIsInstance<JvmDependency>()
            .flatMap { it.classpath }
            .map { it.asNio(project.getEelDescriptor()) }
            .forEach {
                refreshVfs(it)
            }
    }

    private fun refreshVfs(path: Path) {
        if (path.notExists()) return
        if (path.isDirectory()) {
            StandardFileSystems.local()?.refreshAndFindFileByPath(path.pathString)
        } else if (path.isRegularFile()) {
            StandardFileSystems.local()?.refreshAndFindFileByPath(path.pathString)
            StandardFileSystems.jar()?.refreshAndFindFileByPath(path.pathString + URLUtil.JAR_SEPARATOR)
        }
    }

    private suspend fun resolveConfiguration(
        virtualFile: VirtualFile,
        definition: ScriptDefinition,
    ): ScriptCompilationConfigurationResult {
        val providedConfiguration = definition.compilationConfiguration.with {
            configureJdkHome(virtualFile)
        }

        val collectedData = smartReadAction(project) {
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile
                ?: error(KotlinBaseScriptingBundle.message("script.not.kotlin.file", virtualFile.path, virtualFile.fileType.description))
            getScriptCollectedData(ktFile, providedConfiguration, definition.contextClassLoader)
        }

        return refineScriptCompilationConfiguration(
            compilationConfiguration = providedConfiguration,
            sourceCode = VirtualFileScriptSource(virtualFile),
            collectedData = collectedData,
            knownVirtualFileSources = null,
            definition = definition
        )
    }

    private suspend fun resolveConfiguration(
        sourceCode: SourceCode,
        definition: ScriptDefinition,
    ): ScriptCompilationConfigurationResult {
        return when (sourceCode) {
            is KtFileScriptSource -> {
                val collectedData = smartReadAction(project) {
                    getScriptCollectedData(
                        sourceCode.ktFile,
                        definition.compilationConfiguration,
                        definition.contextClassLoader
                    )
                }
                refineScriptCompilationConfiguration(
                    compilationConfiguration = definition.compilationConfiguration,
                    sourceCode = sourceCode,
                    collectedData = collectedData,
                    knownVirtualFileSources = null,
                    definition = definition
                )
            }
            is VirtualFileScriptSource -> resolveConfiguration(sourceCode.virtualFile, definition)
            else -> refineScriptCompilationConfiguration(
                compilationConfiguration = definition.compilationConfiguration,
                sourceCode = sourceCode,
                collectedData = ScriptCollectedData(emptyMap()),
                knownVirtualFileSources = null,
                definition = definition
            )
        }
    }

    /** [sdkId] resolves the script SDK from the SDK table, so a JDK that is absent from that table gives no SDK. */
    fun ScriptCompilationConfiguration.Builder.configureJdkHome(virtualFile: VirtualFile) {
        val jdkHome = get(ide.jdkSupplier)?.invoke(virtualFile)?.absolutePath
            ?: project.javaHomePath?.absolutePath
            ?: ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance())?.homePath
            ?: defaultJavaHome
            ?: return

        if (!application.isUnitTestMode) {
            ExternalSystemJdkUtil.lookupJdkByPath(project, jdkHome)
        }

        jvm.jdkHome(File(jdkHome))
    }

    private suspend fun invalidate(virtualFile: VirtualFile) {
        val entity = project.workspaceModel.currentSnapshot
            .getVirtualFileUrlIndex().findEntitiesByVirtualFile(virtualFile, project.workspaceModel.getVirtualFileUrlManager())
            .filterIsInstance<KotlinScriptEntity>().singleOrNull() ?: return
        project.workspaceModel.update("removing .kts modules") { it.removeEntity(entity) }
    }

    private fun VirtualFile.findScriptDefinition(): ScriptDefinition {
        val definition = findScriptDefinition(project, VirtualFileScriptSource(this))
        scriptingDebugLog {
            val baseDirPath = project.basePath?.toNioPathOrNull()
            val path = baseDirPath?.relativizeToClosestAncestor(this.path)?.second ?: this.path
            "processing script=$path; with definition=${definition.name}(${definition.definitionId})"
        }
        return definition
    }

    @Suppress("unused")
    private class KotlinScriptWorkspaceModelListener(val project: Project) : WorkspaceModelChangeListener {
        override fun beforeChanged(event: VersionedStorageChange) {
            val configurationChanges = event.getChanges(KotlinScriptEntity::class.java)
            if (configurationChanges.any()) {
                dropKotlinScriptCaches(project)
            }
            // A removed or replaced configuration loads again from the current definition.
            configurationChanges.forEach { it.oldEntity?.virtualFileUrl?.virtualFile?.putUserData(RELOAD_NEEDED, null) }
        }

        override fun changed(event: VersionedStorageChange) {
            if (event.getChanges(LibraryEntity::class.java).any() || event.getChanges(ModuleEntity::class.java).any()) {
                DefinitionFromDependenciesProvider.getInstance(project).refresh()
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinScriptService = project.service()

        internal fun dropKotlinScriptCaches(project: Project) {
            ThreadingAssertions.assertWriteAccess()
            ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            HighlightingSettingsPerFile.getInstance(project).incModificationCount()
            project.publishGlobalModuleStateModificationEvent()
            project.publishGlobalScriptModuleStateModificationEvent()
            project.messageBus.syncPublisher(KotlinCompilerPluginsScriptConfigurationListener.TOPIC).scriptConfigurationsChanged()
        }

        private suspend fun collectOpenScriptFiles(project: Project): List<VirtualFile> {
            return readAction {
                FileEditorManager.getInstance(project).openFiles
                    .filter { it.name.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION) }
            }
        }
    }
}

// The IDE turns off ScriptingHostConfiguration.resolveDependencies, so the configuration keeps the coordinates.
// This function resolves them like MainKtsDependencyResolver of the compiler.
private suspend fun ScriptCompilationConfiguration.resolveDependencies(
    project: Project,
    virtualFile: VirtualFile,
): ScriptCompilationConfiguration {
    val allDependencies = get(ScriptCompilationConfiguration.dependencies).orEmpty()
    val coordinates = allDependencies.filterIsInstance<DependencyCoordinates>()
    if (coordinates.isEmpty()) return this

    val repositories = get(ScriptCompilationConfiguration.dependencyRepositories).orEmpty()
    val requests = coordinates.map { it.toRequest(repositories) }
    val service = project.service<ScriptArtifactResolutionService>()

    if (requests.any { service.resolvedOrNull(it) == null }) {
        withBackgroundProgress(
            project,
            KotlinBaseScriptingBundle.message("progress.title.resolving.script.artifacts", virtualFile.name),
        ) {
            service.resolve(requests)
        }
    }

    val resolutions = requests.mapNotNull { service.resolvedOrNull(it) }
    val classpath = resolutions.flatMap { it.classpath }.distinct()
    val messages = resolutions.flatMap { it.messages }
    scriptingDebugLog(virtualFile) {
        "resolved ${coordinates.flatMap { it.artifacts }} to ${classpath.size} files" + messages.joinToString("") { "; $it" }
    }

    if (messages.isNotEmpty()) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("KotlinScriptNotificationGroup")
            .createNotification(
                KotlinBaseScriptingBundle.message("script.configuration.failed", virtualFile.name),
                messages.joinToString("\n"),
                NotificationType.WARNING,
            ).notify(project)
    }

    return ScriptCompilationConfiguration(this) {
        dependencies.put(allDependencies.filterNot { it is DependencyCoordinates })
        updateClasspath(classpath)
    }
}

/** A suspend copy of [org.jetbrains.kotlin.utils.topologicalSort]. */
private suspend fun <A> topologicalSort(
    nodes: Iterable<A>,
    reportCycle: (A) -> Nothing = { throw IllegalStateException("Cannot compute a topological sort: The node $it is in a cycle.") },
    dependencies: suspend A.() -> Iterable<A>,
): List<A> {
    val visiting = mutableSetOf<A>()
    val visited = mutableSetOf<A>()

    suspend fun visit(node: A) {
        if (node in visited) return
        if (node in visiting) reportCycle(node)

        visiting.add(node)
        node.dependencies().forEach { visit(it) }
        visiting.remove(node)

        visited.add(node)
    }

    nodes.forEach { visit(it) }

    return visited.toMutableList()
}

object KotlinScriptEntitySource : EntitySource
