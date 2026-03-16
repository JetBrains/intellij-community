/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.gradle.service.syncAction.impl.bridge

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.workspace.storage.EntitySource
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.WorkspaceEntityBuilder
import com.intellij.platform.workspace.storage.WorkspaceEntityWithSymbolicId
import com.intellij.platform.workspace.storage.impl.WorkspaceEntityBase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.gradle.service.syncAction.GradleEntitySource
import org.jetbrains.plugins.gradle.service.syncAction.GradleSyncPhase


/**
 * This is a temporary, internal API for migration purposes.
 */
@ApiStatus.Internal
class GradleBridgeFinalizerDataService : AbstractProjectDataService<GradleBridgeFinalizerData, Unit>() {
  override fun getTargetDataKey() = GradleBridgeFinalizerData.KEY

  override fun postProcess(toImport: Collection<DataNode<GradleBridgeFinalizerData?>?>,
                           projectData: ProjectData?,
                           project: Project,
                           modelsProvider: IdeModifiableModelsProvider) {
    if (!Registry.`is`("gradle.phased.sync.bridge.disabled") || projectData == null) return

    val currentStorage = modelsProvider.actualStorageBuilder

    val storageBeforeDataServices = modelsProvider.getUserData(SYNC_STORAGE_SNAPSHOT_BEFORE_DATA_SERVICES)!!
    val index = storageBeforeDataServices.entitiesBySource {
      sourceFilter(it, projectData.linkedExternalProjectPath)
    }.associateBy {
      WorkspaceEntityForLookup(it)
    }

    // Go over all the relevant entities and mark the ones that are not originally in the storage before data services execution
    // with an explicit data service source. This is required because some entities otherwise inherit from their parents which are
    // marked entity sources with explicit phases.
    currentStorage.entitiesBySource {
      sourceFilter(it, projectData.linkedExternalProjectPath)
    }.filter {
      if (it is WorkspaceEntityWithSymbolicId) {
        storageBeforeDataServices.resolve(it.symbolicId)
      } else {
        index[WorkspaceEntityForLookup(it)]
      } == null
    }.forEach {
      currentStorage.modifyEntity(WorkspaceEntityBuilder::class.java, it) {
        entitySource = DataServiceEntitySource(projectData.linkedExternalProjectPath)
      }
    }
  }

  private fun sourceFilter(source: EntitySource, linkedExternalProjectPath: String) =
    source is GradleEntitySource && source.projectPath == linkedExternalProjectPath

  /** This is used for looking up entities without a symbolic ID. */
  private class WorkspaceEntityForLookup(entity: WorkspaceEntity) {
    val data = (entity as WorkspaceEntityBase).getData()

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      return data.equalsByKey((other as WorkspaceEntityForLookup).data)
    }

    override fun hashCode() = data.hashCodeByKey()
  }

  private data class DataServiceEntitySource(override val projectPath: String): GradleEntitySource {
    override val phase = GradleSyncPhase.DATA_SERVICES_PHASE
  }
}


