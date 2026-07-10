// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl


import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.jps.entities.SdkRootTypeId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.workspaceModel.core.fileIndex.DependencyDescription
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributorEnforcer
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetRegistrar
import com.intellij.workspaceModel.ide.WsmSingletonEntityUtils
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun EntityStorage.isProjectSdk(entity: SdkEntity): Boolean {
  val setting = WsmSingletonEntityUtils.getSingleEntity(this, ProjectSettingsEntity::class.java)
  return setting?.projectSdk == entity.symbolicId
}

class SdkEntityFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity>, PlatformInternalWorkspaceFileIndexContributor {

  override val entityClass: Class<SdkEntity>
    get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val compiledRootsData: WorkspaceFileSetData
    val sourceRootFileSetData: WorkspaceFileSetData

    if (storage.isProjectSdk(entity)) {
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
      when (root.type) {
        SdkRootTypeId.CLASSES -> registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL, entity, compiledRootsData)
        SdkRootTypeId.SOURCES -> registrar.registerFileSet(root.url, WorkspaceFileKind.EXTERNAL_SOURCE, entity,
                                                                      sourceRootFileSetData)
        else -> {}
      }
    }
  }

  override val dependenciesOnOtherEntities: List<DependencyDescription<SdkEntity>>
    get() = listOf(
      DependencyDescription.OnReference(SdkId::class.java),
    )

  internal open class SdkSourceRootFileSetData(
    sdkId: SdkId,
  ) : SdkRootFileSetData(sdkId), ModuleOrLibrarySourceRootData

  internal open class SdkRootFileSetData(override val sdkId: SdkId) : JvmPackageRootDataInternal, SdkFileSetData {
    override val packagePrefix: String = ""
  }
}

/**
 * Marker for file sets that belong to an SDK.
 *
 * Unlike [SdkEntityFileIndexContributor.SdkRootFileSetData] this interface does NOT extend [JvmPackageRootDataInternal],
 * so implementors that only need SDK attribution (e.g. external annotation roots) can be recognized as SDK file sets
 * without making the root participate in JVM package resolution.
 */
@ApiStatus.Internal
interface SdkFileSetData : WorkspaceFileSetData {
  val sdkId: SdkId
}
