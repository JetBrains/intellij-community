// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.modules

import com.intellij.openapi.extensions.ProjectExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.findEntitiesByVirtualFile
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntitySource

/** Finds the [KotlinScriptEntity] of a script file. */
interface KotlinScriptEntityProvider {
    /** `null` falls back to the URL lookup. */
    fun provide(project: Project, virtualFile: VirtualFile): KotlinScriptEntity? =
        findKotlinScriptEntity(project, virtualFile)

    /** The source of the entity that `KotlinScriptService` creates. `null` falls back to `KotlinScriptEntitySource`. */
    fun entitySource(project: Project, virtualFile: VirtualFile): EntitySource? = null

    companion object {
        fun provide(project: Project, virtualFile: VirtualFile): KotlinScriptEntity? =
            EP_NAME.computeSafeIfAny(project) { it.provide(project, virtualFile) }
                ?: findKotlinScriptEntity(project, virtualFile)

        fun entitySource(project: Project, virtualFile: VirtualFile): EntitySource? =
            EP_NAME.computeSafeIfAny(project) { it.entitySource(project, virtualFile) }

        fun findKotlinScriptEntity(
            project: Project,
            virtualFile: VirtualFile
        ): KotlinScriptEntity? = project.workspaceModel.currentSnapshot
            .getVirtualFileUrlIndex().findEntitiesByVirtualFile(virtualFile, project.workspaceModel.getVirtualFileUrlManager())
            .filterIsInstance<KotlinScriptEntity>().firstOrNull()

        private val EP_NAME: ProjectExtensionPointName<KotlinScriptEntityProvider> =
            ProjectExtensionPointName("org.jetbrains.kotlin.scriptEntityProvider")
    }
}
