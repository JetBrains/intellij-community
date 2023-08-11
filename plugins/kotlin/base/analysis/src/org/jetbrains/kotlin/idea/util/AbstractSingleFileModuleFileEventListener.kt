// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import org.jetbrains.kotlin.analysis.project.structure.KtModule
import org.jetbrains.kotlin.analysis.project.structure.KtNotUnderContentRootModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptDependencyModule
import org.jetbrains.kotlin.analysis.project.structure.KtScriptModule
import org.jetbrains.kotlin.analysis.project.structure.ProjectStructureProvider
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.parsing.KotlinParserDefinition

/**
 * [AbstractSingleFileModuleFileEventListener] listens to file events and calls [processEvent] with the single-file [KtModule] associated
 * with the event's file. An event is only processed if it passes [isRelevantEvent].
 *
 * Use one of the *before* or *after* variants to implement this listener:
 *
 *  - [AbstractSingleFileModuleBeforeFileEventListener]
 *  - [AbstractSingleFileModuleAfterFileEventListener]
 */
sealed class AbstractSingleFileModuleFileEventListener(private val project: Project) : BulkFileListener {
    protected abstract fun isRelevantEvent(event: VFileEvent, file: VirtualFile): Boolean

    protected abstract fun processEvent(event: VFileEvent, module: KtModule)

    internal fun processEvents(events: List<VFileEvent>) {
        for (event in events) {
            val file = event.file ?: continue
            if (!isRelevantEvent(event, file)) continue
            if (!file.mayBeFromSingleFileModule(project)) continue

            // A `BulkFileListener` may receive events from other projects, which need to be ignored. This check is placed after
            // `mayBeFromSingleFileModule` because (1) `mayBeFromSingleFileModule` establishes that `file` is a Kotlin file, and we do not
            // need to check `isInContent` for non-Kotlin files, as we don't want to process them, and (2) for the common case where the
            // user has opened only one project and most Kotlin files are inside a source set, checking `!isInSourceContent` first excludes
            // most files in a single check, as opposed to checking `isInContent` first and then `!isInSourceContent`.
            if (!ProjectFileIndex.getInstance(project).isInContent(file)) continue

            val module = file.getSingleFileModule() ?: continue
            processEvent(event, module)
        }
    }

    /**
     * Checks whether the given [VirtualFile] may come from a single-file [KtModule] with the following heuristics:
     *
     *  1. The file is a script if its extension is `.kts`.
     *  2. The file can only belong to a not-under-content-root `KtModule` if it isn't in some module's source content.
     */
    private fun VirtualFile.mayBeFromSingleFileModule(project: Project): Boolean =
        extension == KotlinParserDefinition.STD_SCRIPT_SUFFIX ||
            isKotlinFileType() && !ProjectFileIndex.getInstance(project).isInSourceContent(this)

    private fun VirtualFile.getSingleFileModule(): KtModule? =
        toPsiFile(project)
            ?.let { ProjectStructureProvider.getModule(project, it, contextualModule = null) }
            ?.takeIf { it is KtScriptModule || it is KtScriptDependencyModule || it is KtNotUnderContentRootModule }
}

abstract class AbstractSingleFileModuleBeforeFileEventListener(project: Project) : AbstractSingleFileModuleFileEventListener(project) {
    override fun before(events: List<VFileEvent>) {
        processEvents(events)
    }
}

abstract class AbstractSingleFileModuleAfterFileEventListener(project: Project) : AbstractSingleFileModuleFileEventListener(project) {
    override fun after(events: List<VFileEvent>) {
        processEvents(events)
    }
}
