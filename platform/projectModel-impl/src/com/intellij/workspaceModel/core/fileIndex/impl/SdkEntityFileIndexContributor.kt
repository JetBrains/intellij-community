// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl


import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.customName
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex

class SdkEntityFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity>, PlatformInternalWorkspaceFileIndexContributor {

  override val entityClass: Class<SdkEntity>
    get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val compiledRootsData = SdkRootFileSetData(entity.symbolicId)
    val sourceRootFileSetData = SdkSourceRootFileSetData(entity.symbolicId)
    for (root in entity.roots) {
      when (root.type.name) {
        OrderRootType.CLASSES.customName -> registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL, entity, compiledRootsData)
        OrderRootType.SOURCES.customName -> registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL_SOURCE, entity,
                                                                      sourceRootFileSetData)
        else -> {}
      }
    }
  }

  internal class SdkSourceRootFileSetData(
    sdkId: SdkId,
  ) : SdkRootFileSetData(sdkId), ModuleOrLibrarySourceRootData

  internal open class SdkRootFileSetData(
    internal val sdkId: SdkId,
  ) : UnloadableFileSetData, JvmPackageRootDataInternal {
    override fun isUnloaded(project: Project): Boolean {
      return !ModuleDependencyIndex.getInstance(project).hasDependencyOn(sdkId)
    }

    override val packagePrefix: String = ""
  }
}
