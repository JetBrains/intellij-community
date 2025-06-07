// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.fir.projectStructure.provider

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.workspace.jps.entities.SourceRootEntity
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule

internal sealed class ModuleCandidate {
    data class Entity(
      val entity: WorkspaceEntity,
      val fileKind: WorkspaceFileKind
    ) : ModuleCandidate()

    data class OutsiderFileEntity(
      val entity: SourceRootEntity,
      val fakeVirtualFile: VirtualFile,
      val originalVirtualFile: VirtualFile
    ) : ModuleCandidate()

    data class Sdk(
        val sdkId: com.intellij.platform.workspace.jps.entities.SdkId
    ) : ModuleCandidate()

    data class FixedModule(
        val module: KaModule
    ) : ModuleCandidate()
}