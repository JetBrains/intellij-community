// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl


import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.workspaceModel.ide.WsmSingletonEntityUtils
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.jps.entities.ProjectSettingsEntity
import com.intellij.platform.workspace.jps.entities.SdkEntity
import com.intellij.platform.workspace.jps.entities.SdkId
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.impl.legacyBridge.sdk.customName
import com.intellij.workspaceModel.ide.legacyBridge.ModuleDependencyIndex

class SdkEntityFileIndexContributor : WorkspaceFileIndexContributor<SdkEntity>, PlatformInternalWorkspaceFileIndexContributor {

  private val useWsmForProjectSdk: Boolean = Registry.`is`("project.root.manager.over.wsm", true)

  override val entityClass: Class<SdkEntity>
    get() = SdkEntity::class.java

  override fun registerFileSets(entity: SdkEntity, registrar: WorkspaceFileSetRegistrar, storage: EntityStorage) {
    val compiledRootsData: WorkspaceFileSetData
    val sourceRootFileSetData: WorkspaceFileSetData

    if (useWsmForProjectSdk && isProjectSdk(entity, storage)) {
      compiledRootsData = SdkRootFileSetData(entity.symbolicId)
      sourceRootFileSetData = SdkSourceRootFileSetData(entity.symbolicId)
    }
    else {
      compiledRootsData = UnloadableSdkRootFileSetData(entity.symbolicId)
      sourceRootFileSetData = UnloadableSdkSourceRootFileSetData(entity.symbolicId)
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
      DependencyDescription.OnArbitraryEntity(ProjectSettingsEntity::class.java) {
        if (it is WorkspaceEntityBase) {
          val jdk = it.projectSdk?.resolve(it.snapshot)
          if (jdk != null) return@OnArbitraryEntity sequenceOf(jdk)
        }

        return@OnArbitraryEntity sequenceOf()
      }
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

  internal class UnloadableSdkSourceRootFileSetData(
    sdkId: SdkId,
  ) : UnloadableSdkRootFileSetData(sdkId), ModuleOrLibrarySourceRootData

  internal open class UnloadableSdkRootFileSetData(sdkId: SdkId) : SdkRootFileSetData(sdkId), UnloadableFileSetData {
    override fun isUnloaded(project: Project): Boolean {
      return !ModuleDependencyIndex.getInstance(project).hasDependencyOn(sdkId)
    }
  }
}
