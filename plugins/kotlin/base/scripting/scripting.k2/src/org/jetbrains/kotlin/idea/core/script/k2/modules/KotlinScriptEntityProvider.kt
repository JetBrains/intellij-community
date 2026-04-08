// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.modules

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel

/**
 * Extension point for locating a [KotlinScriptEntity] in the workspace model for a given script file.
 *
 * Override [provide] when file indirection is needed (e.g. notebook scripts where a virtual
 * cell file must be resolved to the top-level `.ipynb` file before the workspace model lookup).
 * The default implementation performs a direct URL lookup.
 */
interface KotlinScriptEntityProvider {
    /**
     * Returns the [KotlinScriptEntity] for [virtualFile], or `null` if not found.
     * The default implementation performs a direct URL lookup; override to apply file indirection.
     */
    fun provide(project: Project, virtualFile: VirtualFile): KotlinScriptEntity? =
        findKotlinScriptEntity(project, virtualFile)

    companion object {
        fun provide(project: Project, virtualFile: VirtualFile): KotlinScriptEntity? =
            EP_NAME.computeSafeIfAny(project) { it.provide(project, virtualFile) }
                ?: findKotlinScriptEntity(project, virtualFile)

        fun findKotlinScriptEntity(
            project: Project,
            virtualFile: VirtualFile
        ): KotlinScriptEntity? = project.workspaceModel.currentSnapshot
            .getVirtualFileUrlIndex().findEntitiesByUrl(virtualFile.toVirtualFileUrl(project.workspaceModel.getVirtualFileUrlManager()))
            .filterIsInstance<KotlinScriptEntity>().firstOrNull()

        private val EP_NAME: ProjectExtensionPointName<KotlinScriptEntityProvider> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scriptEntityProvider")
    }
}
