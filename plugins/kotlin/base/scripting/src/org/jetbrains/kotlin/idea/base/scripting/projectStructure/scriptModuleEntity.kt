// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.scripting.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.storage.EntityStorage
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun VirtualFile.scriptModuleEntity(project: Project, snapshot: EntityStorage): ModuleEntity? {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val virtualFileUrl = toVirtualFileUrl(virtualFileUrlManager)
    return snapshot.getVirtualFileUrlIndex().findEntitiesByUrl(virtualFileUrl).firstNotNullOfOrNull { it as? ModuleEntity }
}