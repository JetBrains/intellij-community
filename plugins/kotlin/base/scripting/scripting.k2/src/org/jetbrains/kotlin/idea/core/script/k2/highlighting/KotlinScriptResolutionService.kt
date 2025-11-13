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
import com.intellij.util.application
import com.intellij.util.concurrency.ThreadingAssertions
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalModuleStateModificationEvent
import org.jetbrains.kotlin.analysis.api.platform.modification.publishGlobalScriptModuleStateModificationEvent
import org.jetbrains.kotlin.idea.core.script.k2.configurations.getConfigurationProviderExtension
import org.jetbrains.kotlin.idea.core.script.shared.KotlinScriptProcessingFilter
import org.jetbrains.kotlin.idea.core.script.v1.ScriptDependenciesModificationTracker
import org.jetbrains.kotlin.idea.core.script.v1.alwaysVirtualFile
import org.jetbrains.kotlin.idea.core.script.v1.awaitExternalSystemInitialization
import org.jetbrains.kotlin.idea.core.script.v1.scriptingDebugLog
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.findScriptDefinition
import org.jetbrains.kotlin.scripting.resolve.KtFileScriptSource
import org.jetbrains.kotlin.scripting.resolve.ScriptCompilationConfigurationResult


/**
 * Project-level service that resolves Kotlin script files and prepares IDE highlighting for them.
 *
 * Responsibilities:
 * - Decides whether a script should be processed using [KotlinScriptProcessingFilter].
 * - Finds the appropriate [ScriptDefinition] for each `KtFile`.
 * - Resolves or creates script configurations via the definition-specific configuration resolver.
 * - Updates the workspace model with the resolved configurations.
 * - Invalidates caches and publishes module/script modification events so that highlighting is updated.
 *
 * This service is coroutine-aware: processing can be launched from a background coroutine, and
 * cache invalidation that requires EDT write access is performed via [edtWriteAction].
 *
 * Typical usage: call [launchProcessing] from listeners reacting to script changes, or
 * call the suspend [process] functions from a coroutine.
 */
@Service(Service.Level.PROJECT)
class KotlinScriptResolutionService(
    val project: Project,
    val coroutineScope: CoroutineScope,
) {
    /**
     * Launches asynchronous processing for a single script file on [coroutineScope].
     *
     * This is a fire-and-forget entry point suitable for UI or VFS event listeners.
     * If you already are in a coroutine, prefer the suspend [process] overloads.
     *
     * @param ktFile the Kotlin script to be processed.
     */
    fun launchProcessing(ktFile: KtFile) {
        coroutineScope.launchTracked {
            project.awaitExternalSystemInitialization()
            process(listOf(ktFile))
        }
    }

    /**
     * Suspends and processes a single Kotlin script file, preparing its highlighting.
     * Internally delegates to [process] for multiple files.
     *
     * @param ktFile the script to resolve and prepare for highlighting.
     */
    suspend fun process(ktFile: KtFile) {
        process(listOf(ktFile))
    }

    /**
     * Suspends and processes the given collection of Kotlin script files.
     *
     * Steps performed:
     * 1. Filter scripts that should not be processed via [KotlinScriptProcessingFilter].
     * 2. Resolve a [ScriptDefinition] for each file.
     * 3. Resolve or create per-file script configurations and update the workspace model.
     * 4. Invalidate related caches and publish modification events so the IDE re-highlights.
     *
     * If the input is empty or every file is filtered out, the call returns immediately.
     *
     * @param ktFiles script files to process.
     */
    suspend fun process(ktFiles: Iterable<KtFile>) {
        if (ktFiles.none() || ktFiles.any { !KotlinScriptProcessingFilter.shouldProcessScript(it) }) return

        val definitionByFile = ktFiles.associateWith { it.findScriptDefinition() }
        prepareHighlighting(definitionByFile)
    }

    private fun KtFile.findScriptDefinition(): ScriptDefinition {
        val definition = findScriptDefinition(project, KtFileScriptSource(this))

        scriptingDebugLog {
            val baseDirPath = project.basePath?.toNioPathOrNull()
            val path = baseDirPath?.relativizeToClosestAncestor(this.alwaysVirtualFile.path)?.second ?: this.alwaysVirtualFile.path

            "processing script=$path; with definition=${definition.name}(${definition.definitionId})"
        }

        return definition
    }

    /**
     * Resolves configurations for the given files and updates the workspace model accordingly.
     *
     * For each `(file, definition)\` pair:
     * - Obtains a configuration supplier from the [ScriptDefinition].
     * - Tries to get an existing configuration for the file; if absent, creates it.
     * - Accumulates configurations and applies them to the workspace model in a single batch update.
     *
     * @param definitionByFile mapping of script files to their resolved [ScriptDefinition].
     */
    private suspend fun prepareHighlighting(definitionByFile: Map<KtFile, ScriptDefinition>) {
        val configurationProviderExtension = definitionByFile.firstNotNullOf { it.value.getConfigurationProviderExtension(project) }

        assert(!application.isWriteAccessAllowed)
        val configurationByFile = mutableMapOf<VirtualFile, ScriptCompilationConfigurationResult>()
        for ((file, definition) in definitionByFile) {
            val virtualFile = file.virtualFile
            configurationByFile[virtualFile] = configurationProviderExtension.get(project, virtualFile)
                ?: configurationProviderExtension.create(virtualFile, definition) ?: continue
        }
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): KotlinScriptResolutionService = project.service()

        fun dropKotlinScriptCaches(project: Project) {
            ThreadingAssertions.assertWriteAccess()

            ScriptDependenciesModificationTracker.getInstance(project).incModificationCount()
            HighlightingSettingsPerFile.getInstance(project).incModificationCount()

            project.publishGlobalModuleStateModificationEvent()
            project.publishGlobalScriptModuleStateModificationEvent()
        }
    }
}