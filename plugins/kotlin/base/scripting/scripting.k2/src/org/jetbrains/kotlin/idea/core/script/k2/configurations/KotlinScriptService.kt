// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.configurations

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
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
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.awaitExternalSystemInitialization
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.refineScriptCompilationConfiguration
import org.jetbrains.kotlin.utils.topologicalSort
import java.io.File
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.api.valueOrNull
import kotlin.script.experimental.api.with
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

    private suspend fun update(virtualFile: VirtualFile, definition: ScriptDefinition) {
        if (KotlinScriptEntityProvider.provide(project, virtualFile) != null) return

        val urlManager = project.workspaceModel.getVirtualFileUrlManager()
        val scriptUrl = virtualFile.toVirtualFileUrl(urlManager)

        val (rootConfiguration, importedConfigurations) = try {
            val rootConfiguration = resolveConfiguration(virtualFile, definition)
            val importedConfigurations = topologicalSort(nodes = listOf(rootConfiguration), reportCycle = {
                throw IllegalStateException(
                    KotlinBaseScriptingBundle.message(
                        "script.part.circular.file.import.chain",
                        it.valueOrNull()?.script?.name ?: "<null>"
                    )
                )
            }) {
                this.valueOrNull()?.importedScripts.orEmpty().map {
                    refineScriptCompilationConfiguration(it, definition, project)
                }
            }.reversed()

            rootConfiguration to importedConfigurations
        } catch (e: Throwable) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("KotlinScriptNotificationGroup")
                .createNotification(
                    KotlinBaseScriptingBundle.message("circular.script.import"),
                    e.message ?: KotlinBaseScriptingBundle.message("script.configuration.failed.unknown", virtualFile.name),
                    NotificationType.ERROR,
                )
                .notify(project)
            return
        }

        if (rootConfiguration is ResultWithDiagnostics.Failure) {
            rootConfiguration.reports.forEach {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("KotlinScriptNotificationGroup")
                    .createNotification(
                        KotlinBaseScriptingBundle.message("script.configuration.failed", virtualFile.name),
                        it.message,
                        when (it.severity) {
                            ScriptDiagnostic.Severity.FATAL, ScriptDiagnostic.Severity.ERROR -> NotificationType.ERROR
                            ScriptDiagnostic.Severity.WARNING -> NotificationType.WARNING
                            else -> NotificationType.ERROR
                        },
                    )
                    .notify(project)
                return
            }
            return
        }

        project.workspaceModel.update("updating kotlin script entities [$KotlinScriptEntitySource]") { storage ->
            if (!storage.containsScriptEntity(scriptUrl)) {
                val configuration = rootConfiguration.valueOrNull()?.configuration ?: return@update
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

    private suspend fun resolveConfiguration(
        virtualFile: VirtualFile,
        definition: ScriptDefinition,
    ): ScriptCompilationConfigurationResult {
        val projectSdk = ProjectRootManager.getInstance(project).projectSdk?.homePath
        val configuration = definition.compilationConfiguration.with {
            projectSdk?.let { jvm.jdkHome(File(it)) }
        }
        val scriptSource = VirtualFileScriptSource(virtualFile)
        return withBackgroundProgress(
            project, title = KotlinBaseScriptingBundle.message("progress.title.dependency.resolution", virtualFile.name)
        ) {
            smartReadAction(project) {
                refineScriptCompilationConfiguration(scriptSource, definition, project, configuration)
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
    }
}

object KotlinScriptEntitySource : EntitySource
