// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityStorageSnapshot
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.toBuilder

object WorkspaceEntityLifecycleSupporterUtils {
  fun ensureEntitiesInWorkspaceAreAsProviderDefined(project: Project, provider: WorkspaceEntityLifecycleSupporter<*>) {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val builderRef: Ref<MutableEntityStorage?> = Ref.create()
    val snapshot = workspaceModel.currentSnapshot

    ensureInitialized(project, provider, snapshot, builderRef)

    builderRef.get()?.also { builder -> WriteAction.runAndWait<RuntimeException> { writeBuilder(workspaceModel, builder) } }
  }

  suspend fun ensureAllEntitiesInWorkspaceAreAsProvidersDefined(project: Project) {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val snapshot = workspaceModel.currentSnapshot
    val builderRef: Ref<MutableEntityStorage?> = Ref.create()

    WorkspaceEntityLifecycleSupporter.EP_NAME.forEachExtensionSafe { provider ->
      ensureInitialized(project, provider, snapshot, builderRef)
    }
    builderRef.get()?.also { builder -> writeAction { writeBuilder(workspaceModel, builder) } }
  }


  private fun writeBuilder(workspaceModel: WorkspaceModel,
                           builder: MutableEntityStorage) {
    workspaceModel.updateProjectModel("ConstantEntitiesCheckActivity") { tempBuilder: MutableEntityStorage ->
      tempBuilder.addDiff(builder)
    }
  }

  private fun <E : WorkspaceEntity> ensureInitialized(project: Project,
                                                      provider: WorkspaceEntityLifecycleSupporter<E>,
                                                      snapshot: EntityStorageSnapshot,
                                                      builderRef: Ref<MutableEntityStorage?>) {
    val expectedEntity = provider.createSampleEntity(project)
    val actualEntities = snapshot.entities(provider.getEntityClass()).toList()

    if (expectedEntity == null && actualEntities.isEmpty()) {
      return
    }

    if (expectedEntity != null && actualEntities.size == 1 && provider.haveEqualData(expectedEntity, actualEntities[0])) {
      return
    }

    if (builderRef.isNull) {
      builderRef.set(snapshot.toBuilder())
    }
    val builder = builderRef.get()!!

    for (oldEntity in actualEntities) {
      builder.removeEntity(oldEntity)
    }

    if (expectedEntity != null) {
      builder.addEntity(expectedEntity)
    }
  }
}