// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.utils.asNio
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.io.URLUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalScriptModuleStateModificationEvent
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.k2.definitions.ScriptDefinitionsModificationTracker
import org.jetbrains.kotlin.idea.core.script.k2.getOrCreateScriptConfigurationId
import org.jetbrains.kotlin.idea.core.script.k2.getVirtualFile
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.k2.modules.modifyKotlinScriptLibraryEntity
import org.jetbrains.kotlin.idea.core.script.shared.KotlinBaseScriptingBundle
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptProcessingFilter
import org.jetbrains.kotlin.idea.core.script.shared.definition.javaHomePath
import org.jetbrains.kotlin.idea.core.script.shared.definition.jdkSupplier
import org.jetbrains.kotlin.idea.core.script.shared.smartRefineScriptCompilationConfiguration
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.awaitExternalSystemInitialization
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationWrapper
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.notExists
import kotlin.io.path.pathString
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.dependencies
import kotlin.script.experimental.api.dependenciesSources
import kotlin.script.experimental.api.ide
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.jdkHome
import kotlin.script.experimental.jvm.jvm

/**
 * Project-level service responsible for managing the full lifecycle of Kotlin script configurations.
 *
 * Responsibilities:
 * - Decides whether a script should be processed using [KotlinScriptProcessingFilter].
 * - Locates the appropriate [ScriptDefinition] for each script file.
 * - Resolves script configurations and writes workspace model entities.
 * - Invalidates entries when scripts are deleted or reloaded.
 * - Drops analysis caches and publishes modification events when entities change.
 *
 * Threading:
 * - Use [scheduleLoading] for fire-and-forget processing from listeners (e.g., VFS or editor events).
 *   If you are already inside a coroutine, prefer the suspend [load] overloads.
 * - External system initialization is awaited before processing begins, see [awaitExternalSystemInitialization].
 */
@Service(Service.Level.PROJECT)
class KotlinScriptService(val project: Project, val coroutineScope: CoroutineScope) {

    /**
     * Launches asynchronous processing for a single `.kts` file on [coroutineScope].
     * Safe to call from any thread; awaits external system initialization.
     */
    fun scheduleLoading(virtualFile: VirtualFile) {
        coroutineScope.launchTracked {
            project.awaitExternalSystemInitialization()
            if (virtualFile.name.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION)) {
                load(virtualFile)
            }
        }
    }

    /**
     * Resolves the script and updates its workspace model entity.
     * Skips files rejected by [KotlinScriptProcessingFilter].
     */
    suspend fun load(virtualFile: VirtualFile) {
        if (!KotlinScriptProcessingFilter.shouldProcessScript(project, virtualFile)) return
        assert(!application.isWriteAccessAllowed)
        update(virtualFile, virtualFile.findScriptDefinition())
    }

    suspend fun reload(virtualFile: VirtualFile) {
        invalidate(virtualFile)
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
        load(virtualFile)
    }

    /**
     * Drops all entities contributed under [KotlinScriptEntitySource], then rebuilds only the currently open `.kts` files.
     * Precise owner-specific entities survive because they use a different entity source.
     * Closed generic scripts are restored lazily on the next editor-driven load.
     */
    fun scheduleReloadOpenScripts(): Job = coroutineScope.launchTracked {
        invalidateAll()
        ScriptDefinitionsModificationTracker.getInstance(project).incModificationCount()
        for (openFile in collectOpenScriptFiles(project)) {
            load(openFile)
        }
    }

    private suspend fun update(virtualFile: VirtualFile, definition: ScriptDefinition) {
        if (KotlinScriptEntityProvider.provide(project, virtualFile) != null) return

        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val scriptUrl = virtualFile.toVirtualFileUrl(urlManager)

        val (rootConfiguration, importedConfigurations) = try {
            val rootConfiguration = resolveConfiguration(virtualFile, definition)
            val importedConfigurations = topologicalSort(nodes = listOf(rootConfiguration), reportCycle = {
                throw IllegalStateException(
                    KotlinBaseScriptingBundle.message(
                        "script.part.circular.file.import.chain", it.valueOrNull()?.script?.name ?: "<null>"
                    )
                )
            }) {
                this.valueOrNull()?.importedScripts.orEmpty().map {
                    smartRefineScriptCompilationConfiguration(it, definition, project, null)
                }
            }.reversed()

            rootConfiguration to importedConfigurations
        } catch (e: Throwable) {
            NotificationGroupManager.getInstance().getNotificationGroup("KotlinScriptNotificationGroup").createNotification(
                KotlinBaseScriptingBundle.message("circular.script.import"),
                e.message ?: KotlinBaseScriptingBundle.message("script.configuration.failed.unknown", virtualFile.name),
                NotificationType.ERROR,
            ).notify(project)
            return
        }

        val configuration = when (rootConfiguration) {
            is ResultWithDiagnostics.Success<ScriptCompilationConfigurationWrapper> -> rootConfiguration.value.configuration
            is ResultWithDiagnostics.Failure -> {
                rootConfiguration.reports.forEach {
                    NotificationGroupManager.getInstance().getNotificationGroup("KotlinScriptNotificationGroup").createNotification(
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

        configuration.refreshVfsDependencies()

        project.workspaceModel.update("updating kotlin script entities [$KotlinScriptEntitySource]") { storage ->
            if (!storage.containsScriptEntity(scriptUrl)) {
                val libraryIds = generateScriptLibraryEntities(project, configuration, definition).toList()
                for ((id, sources) in libraryIds) {
                    val existingLibrary = storage.resolve(id)
                    if (existingLibrary == null) {
                        storage addEntity KotlinScriptLibraryEntity(id.classes, setOf(scriptUrl), KotlinScriptEntitySource) {
                            this.sources += sources
                        }
                    } else {
                        storage.modifyKotlinScriptLibraryEntity(existingLibrary) {
                            this.sources += sources
                            this.usedInScripts += scriptUrl
                        }
                    }
                }
                storage addEntity KotlinScriptEntity(scriptUrl, libraryIds.map { it.first }, KotlinScriptEntitySource) {
                    this.configurationId = configuration.getOrCreateScriptConfigurationId(storage, KotlinScriptEntitySource)
                    this.sdkId = configuration.sdkId
                }
            }
        }

        for (importedConfig in importedConfigurations) {
            val script = importedConfig.valueOrNull()?.script ?: continue
            val importedFile = getVirtualFile(script) ?: continue
            update(importedFile, importedFile.findScriptDefinition())
        }
    }

    // Refresh VFS for any dependency JARs that may have been re-downloaded from the last VFS scan.
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
        val configuration = definition.compilationConfiguration.withUpdatedJdkHome(virtualFile)
        val scriptSource = VirtualFileScriptSource(virtualFile)
        return withBackgroundProgress(
            project, title = KotlinBaseScriptingBundle.message("progress.title.dependency.resolution", virtualFile.name)
        ) {
            smartRefineScriptCompilationConfiguration(scriptSource, definition, project, configuration)
        }
    }

    fun ScriptCompilationConfiguration.withUpdatedJdkHome(virtualFile: VirtualFile): ScriptCompilationConfiguration {
        return with {
            val jdk = get(ide.jdkSupplier)?.invoke(virtualFile) ?: project.javaHomePath
            if (jdk != null) {
                jvm.jdkHome(jdk)
            }
        }
    }

    private suspend fun invalidate(virtualFile: VirtualFile) {
        val url = virtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager())
        val entity = project.workspaceModel.currentSnapshot
            .getVirtualFileUrlIndex().findEntitiesByUrl(url)
            .filterIsInstance<KotlinScriptEntity>().singleOrNull() ?: return
        project.workspaceModel.update("removing .kts modules") { it.removeEntity(entity) }
    }

    private suspend fun invalidateAll() {
        project.workspaceModel.update("removing all .kts entities") { storage ->
            storage.replaceBySource({ it == KotlinScriptEntitySource }, ImmutableEntityStorage.empty())
        }
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
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinScriptService = project.service()

        @OptIn(KaPlatformInterface::class)
        private fun dropKotlinScriptCaches(project: Project) {
            ThreadingAssertions.assertWriteAccess()
            ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            HighlightingSettingsPerFile.getInstance(project).incModificationCount()
            project.publishGlobalModuleStateModificationEvent()
            project.publishGlobalScriptModuleStateModificationEvent()
        }

        private suspend fun collectOpenScriptFiles(project: Project): List<VirtualFile> {
            return readAction {
                FileEditorManager.getInstance(project).openFiles
                    .filter { it.name.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION) }
            }
        }
    }
}

/**
 * suspendable version of [org.jetbrains.kotlin.utils.topologicalSort]
 */
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

        // Keeping track of the nodes that are being visited allows the algorithm to throw an exception in case of a cycle. The input should
        // never be cyclic, but this approach gives some additional safety in case of bugs.
        visiting.add(node)
        node.dependencies().forEach { visit(it) }
        visiting.remove(node)

        visited.add(node)
    }

    nodes.forEach { visit(it) }

    // The paper algorithm inserts nodes at the head of the result list. Because our `visited` set remembers elements in their order of
    // insertion, the result needs to be reversed.
    return visited.toMutableList()
}

object KotlinScriptEntitySource : EntitySource
