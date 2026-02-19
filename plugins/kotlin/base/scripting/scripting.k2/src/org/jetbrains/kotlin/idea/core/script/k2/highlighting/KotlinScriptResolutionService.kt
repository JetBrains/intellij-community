// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.highlighting

import com.intellij.codeInsight.daemon.impl.analysis.HighlightingSettingsPerFile
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.relativizeToClosestAncestor
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.launchTracked
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.analysis.api.KaPlatformInterface
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalScriptModuleStateModificationEvent
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getScriptEntityProvider
import org.jetbrains.kotlin.idea.core.script.k2.modules.KotlinScriptEntity
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptProcessingFilter
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.awaitExternalSystemInitialization
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.VirtualFileScriptSource


/**
 * Project-level service responsible for resolving Kotlin script (`.kts`) files and preparing IDE highlighting for them (K2).
 *
 * Responsibilities:
 * - Decides whether a script should be processed using [KotlinScriptProcessingFilter].
 * - Locates the appropriate [ScriptDefinition] for each script file.
 * - Obtains a definition-specific configuration provider and resolves/creates script configurations.
 * - Triggers cache invalidation and publishes module/script modification events so that analysis and highlighting are refreshed.
 *
 * Threading and lifecycle:
 * - This service is coroutine-aware. Use [launchProcessing] for fire-and-forget processing from listeners (e.g., VFS or editor events).
 *   If you are already inside a coroutine, prefer the suspend [process] overloads.
 * - Processing runs off the EDT. Any required write actions (e.g., cache invalidation) are performed via [edtWriteAction].
 * - External system initialization is awaited before processing begins, see [awaitExternalSystemInitialization].
 *
 * Notes:
 * - Only files ending with [KotlinFileType.DOT_SCRIPT_EXTENSION] are handled.
 */
@Service(Service.Level.PROJECT)
class KotlinScriptResolutionService(
    val project: Project,
    val coroutineScope: CoroutineScope,
) {
    /**
     * Launches asynchronous processing for a single `.kts` file on [coroutineScope].
     *
     * Intended for UI-/VFS-driven listeners (fire-and-forget). If you already run in a coroutine,
     * prefer calling a suspend [process] overload directly.
     *
     * Behavior:
     * - Waits for external systems to initialize.
     * - Skips non-`.kts` files.
     * - Delegates to [process] for the actual work.
     *
     * This method is safe to call from any thread.
     *
     * @param virtualFile the script file to process.
     */
    fun launchProcessing(virtualFile: VirtualFile) {
        coroutineScope.launchTracked {
            project.awaitExternalSystemInitialization()
            // TODO: investigate if we can check virtualFile.isScript() or smth
            if (virtualFile.name.endsWith(KotlinFileType.DOT_SCRIPT_EXTENSION)) {
                process(listOf(virtualFile))
            }
        }
    }

    /**
     * Suspends and processes a single Kotlin script file, preparing it for analysis/highlighting.
     * Internally delegates to [process] for multiple files.
     *
     * @param virtualFile the script to resolve and prepare for highlighting.
     */
    suspend fun process(virtualFile: VirtualFile) {
        process(listOf(virtualFile))
    }

    /**
     * Suspends and resolves a batch of Kotlin scripts for K2 analysis/highlighting.
     *
     * Pipeline:
     * 1. Validates input: returns immediately if the collection is empty or if any file is not eligible
     *    according to `KotlinScriptProcessingFilter.shouldProcessScript(project, file)`.
     * 2. For each file, locates its `ScriptDefinition` (via `findScriptDefinition`).
     * 3. Obtains a definition-specific entity provider (`getScriptEntityProvider(project)`) using
     *    the first definition that reports a provider. The same provider is then used for all files in the batch.
     * 4. Ensures a `KotlinScriptEntity` exists for each file by reusing an existing entity (`getKotlinScriptEntity`)
     *    or creating one (`updateWorkspaceModel(virtualFile, definition)`).
     *
     * Threading:
     * - Must be called without write access; the method asserts this (`!application.isWriteAccessAllowed`).
     * - Any necessary write actions for workspace model updates are handled inside the entity provider.
     *
     * This function prepares script entity so that downstream caches/analysis can highlight the scripts correctly.
     * Cache invalidation and event publication happen reactively via workspace model listeners (see
     * `KotlinScriptWorkspaceModelListener`).
     *
     * @param virtualFiles A collection of `.kts` files to resolve
     */
    suspend fun process(virtualFiles: Iterable<VirtualFile>) {
        if (virtualFiles.none() || virtualFiles.any { !KotlinScriptProcessingFilter.shouldProcessScript(project, it) }) return

        val definitionByFile = virtualFiles.associateWith { it.findScriptDefinition() }
        val entityProvider = definitionByFile.firstNotNullOf { it.value.getScriptEntityProvider(project) }

        assert(!application.isWriteAccessAllowed)
        definitionByFile.forEach { (virtualFile, definition) ->
            with(entityProvider) {
                getKotlinScriptEntity(virtualFile) ?: updateWorkspaceModel(virtualFile, definition)
            }
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
        fun getInstance(project: Project): KotlinScriptResolutionService = project.service()

        /**
         * Drops script-related caches and publishes module/script modification events.
         *
         * Must be called under a write action. It:
         * - Increments [ScriptDependenciesModificationTracker].
         * - Increments [HighlightingSettingsPerFile] modification count to refresh daemon highlighting.
         * - Publishes global module and script module state modification events to re-run analysis.
         */
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