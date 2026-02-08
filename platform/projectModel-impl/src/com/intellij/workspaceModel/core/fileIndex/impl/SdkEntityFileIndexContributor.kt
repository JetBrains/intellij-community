// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl


import com.intellij.openapi.roots.OrderRootType
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributorEnforcer
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.WsmSingletonEntityUtils
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.customName

class SdkEntityFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity>, PlatformInternalWorkspaceFileIndexContributor {

  override val entityClass: Class<SdkEntity>
    get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val compiledRootsData: WorkspaceFileSetData
    val sourceRootFileSetData: WorkspaceFileSetData

    if (isProjectSdk(entity, storage)) {
      compiledRootsData = SdkRootFileSetData(entity.symbolicId)
      sourceRootFileSetData = SdkSourceRootFileSetData(entity.symbolicId)
    }
    else {
      val enforced = WorkspaceFileIndexContributorEnforcer.EP_NAME
        .extensionsIfPointIsRegistered.any { it.shouldContribute(entity, storage) }
      if (!enforced && !storage.hasReferrers(entity.symbolicId)) {
        return
      }
      compiledRootsData = SdkRootFileSetData(entity.symbolicId)
      sourceRootFileSetData = SdkSourceRootFileSetData(entity.symbolicId)
    }

    for (root in entity.roots) {
      when (root.type.name) {
        OrderRootType.CLASSES.customName -> registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL, entity, compiledRootsData)
        OrderRootType.SOURCES.customName -> registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL_SOURCE, entity,
                                                                      sourceRootFileSetData)
        else -> {}
      }
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<SdkEntity>>
    get() = listOf(
      DependencyDescription.OnReference(SdkId::class.java),
    )

  private fun isProjectSdk(entity: SdkEntity, storage: EntityStorage): Boolean {
    val setting = WsmSingletonEntityUtils.getSingleEntity(storage, ProjectSettingsEntity::class.java)
    return setting?.projectSdk == entity.symbolicId
  }

  internal open class SdkSourceRootFileSetData(
    sdkId: SdkId,
  ) : SdkRootFileSetData(sdkId), ModuleOrLibrarySourceRootData

  internal open class SdkRootFileSetData(internal val sdkId: SdkId) : JvmPackageRootDataInternal {
    override val packagePrefix: String = ""
  }
}
