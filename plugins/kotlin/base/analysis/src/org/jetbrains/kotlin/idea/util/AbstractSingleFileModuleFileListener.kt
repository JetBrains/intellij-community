// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.util

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
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
 * [AbstractSingleFileModuleFileListener] listens to file events and calls [processEvent] with the [KtModule] associated with the script or
 * not-under-content-root file of an event. An event is only processed if it passes [shouldProcessEvent].
 */
abstract class AbstractSingleFileModuleFileListener(private val project: Project) : BulkFileListener {
    protected abstract fun shouldProcessEvent(event: VFileEvent): Boolean

    protected abstract fun processEvent(event: VFileEvent, module: KtModule)

    override fun after(events: List<VFileEvent>) {
        for (event in events) {
            if (!shouldProcessEvent(event)) continue
            val file = event.file ?: continue

            if (file.mayBeFromSingleFileModule()) {
                val module =
                    file.toPsiFile(project)?.let { ProjectStructureProvider.getModule(project, it, contextualModule = null) } ?: continue

                if (module is KtScriptModule || module is KtScriptDependencyModule || module is KtNotUnderContentRootModule) {
                    processEvent(event, module)
                }
            }
        }
    }

    /**
     * The file is a script if its extension is `.kts`. Alternatively, the file can only belong to a not-under-content-root `KtModule` if it
     * has no module associated with it.
     */
    private fun VirtualFile.mayBeFromSingleFileModule(): Boolean =
        extension == KotlinParserDefinition.STD_SCRIPT_SUFFIX ||
            isKotlinFileType() && ModuleUtilCore.findModuleForFile(this, project) == null
}
