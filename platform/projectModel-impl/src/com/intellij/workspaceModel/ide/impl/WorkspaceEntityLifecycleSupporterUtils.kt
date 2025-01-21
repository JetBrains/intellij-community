// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.backgroundWriteAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.platform.backend.workspace.WorkspaceEntityLifecycleSupporter
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.ImmutableEntityStorage
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.toBuilder
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
object WorkspaceEntityLifecycleSupporterUtils {
  suspend fun ensureEntitiesInWorkspaceAreAsProviderDefined(project: Project, provider: WorkspaceEntityLifecycleSupporter<*, *>) {
    val workspaceModel = project.serviceAsync<WorkspaceModel>()
    val builderRef = Ref<MutableEntityStorage>()
    val snapshot = workspaceModel.currentSnapshot
    ensureInitialized(project = project, provider = provider, snapshot = snapshot, builderRef = builderRef)

    builderRef.get()?.let { backgroundWriteAction { writeBuilder(workspaceModel, it) } }
  }

  suspend fun ensureAllEntitiesInWorkspaceAreAsProvidersDefined(project: Project) {
    val workspaceModel = project.serviceAsync<WorkspaceModel>()
    val snapshot = workspaceModel.currentSnapshot
    val builderRef = Ref<MutableEntityStorage>()

    WorkspaceEntityLifecycleSupporter.EP_NAME.forEachExtensionSafe { provider ->
      ensureInitialized(project = project, provider = provider, snapshot = snapshot, builderRef = builderRef)
    }
    builderRef.get()?.let { backgroundWriteAction { writeBuilder(workspaceModel, it) } }
  }

  @TestOnly
  fun withAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(project: Project, block: () -> Unit) {
    setUpAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(project)
    try {
      block()
    }
    finally {
      tearDownAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(project)
    }
  }

  @TestOnly
  fun setUpAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(project: Project) {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val snapshot = workspaceModel.currentSnapshot
    val builderRef = Ref<MutableEntityStorage>()

    WorkspaceEntityLifecycleSupporter.EP_NAME.forEachExtensionSafe { provider ->
      ensureInitialized(project = project, provider = provider, snapshot = snapshot, builderRef = builderRef)
    }
    builderRef.get()?.let { ApplicationManager.getApplication().runWriteAction { writeBuilder(workspaceModel, it) } }
  }

  @TestOnly
  fun tearDownAllEntitiesInWorkspaceFromProvidersDefinedOnEdt(project: Project) {
    val workspaceModel = WorkspaceModel.getInstance(project)
    val snapshot = workspaceModel.currentSnapshot
    val builderRef = Ref<MutableEntityStorage>()
    WorkspaceEntityLifecycleSupporter.EP_NAME.forEachExtensionSafe { provider ->
      val entitiesIterator = snapshot.entities(provider.getEntityClass()).iterator()
      if (entitiesIterator.hasNext()) {
        var builder = builderRef.get()
        if (builder == null) {
          builder = snapshot.toBuilder()
          builderRef.set(builder)
        }

        entitiesIterator.forEach { builder.removeEntity(it) }
      }
    }
    builderRef.get()?.let { ApplicationManager.getApplication().runWriteAction { writeBuilder(workspaceModel, it) } }
  }

  private fun writeBuilder(workspaceModel: WorkspaceModel, builder: MutableEntityStorage) {
    workspaceModel.updateProjectModel("ConstantEntitiesCheckActivity") {
      it.applyChangesFrom(builder)
    }
  }

  private fun <E : WorkspaceEntity, M : WorkspaceEntity.Builder<E>> ensureInitialized(
    project: Project,
    provider: WorkspaceEntityLifecycleSupporter<E, M>,
    snapshot: ImmutableEntityStorage,
    builderRef: Ref<MutableEntityStorage>,
  ) {
    val expectedEntity = provider.createSampleEntity(project)
    val actualEntities = snapshot.entities(provider.getEntityClass()).toList()

    if (expectedEntity == null && actualEntities.isEmpty()) {
      return
    }

    if (expectedEntity != null && actualEntities.size == 1 && provider.haveEqualData(expectedEntity, actualEntities[0])) {
      return
    }

    var builder = builderRef.get()
    if (builder == null) {
      builder = snapshot.toBuilder()
      builderRef.set(builder)
    }

    for (oldEntity in actualEntities) {
      builder.removeEntity(oldEntity)
    }

    if (expectedEntity != null) {
      builder.addEntity(expectedEntity)
    }
  }
}