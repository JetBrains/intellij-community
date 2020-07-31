// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeProjectLifecycleListener
import com.intellij.workspaceModel.storage.*

class WorkspaceModelImpl(project: Project): WorkspaceModel, Disposable {

  private val projectEntities: WorkspaceEntityStorageBuilder

  private val cacheEnabled = !ApplicationManager.getApplication().isUnitTestMode && LegacyBridgeProjectLifecycleListener.cacheEnabled
  private val cache = if (cacheEnabled) WorkspaceModelCacheImpl(project, this) else null

  override val entityStorage: VersionedEntityStorageImpl

  init {
    // TODO It's possible to load this cache from the moment we know project path
    //  Like in ProjectLifecycleListener or something

    entityStorage = ProjectModelEntityStorage(project, WorkspaceEntityStorageBuilder.create().toStorage())

    val initialContent = WorkspaceModelInitialTestContent.pop()
    projectEntities = when {
      initialContent != null -> {
        val testEntities = WorkspaceEntityStorageBuilder.from(initialContent)
        entityStorage.replace(testEntities.toStorage(), testEntities.collectChanges(WorkspaceEntityStorageBuilder.create()))
        testEntities
      }
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("(wm) Loading cache")
        val cacheEntities = cache.loadCache() ?: WorkspaceEntityStorageBuilder.create()
        activity.end()
        entityStorage.replace(cacheEntities.toStorage(), cacheEntities.collectChanges(WorkspaceEntityStorageBuilder.create()))
        cacheEntities
      }
      else -> WorkspaceEntityStorageBuilder.create()
    }
  }

  override fun <R> updateProjectModel(updater: (WorkspaceEntityStorageBuilder) -> R): R = doUpdateProject(updater, true)

  override fun <R> updateProjectModelSilent(updater: (WorkspaceEntityStorageBuilder) -> R): R = doUpdateProject(updater, false)

  // TODO We need transaction semantics here, failed updates should not poison everything
  private fun <R> doUpdateProject(updater: (WorkspaceEntityStorageBuilder) -> R, notify: Boolean): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val before = projectEntities.toStorage()

    val result = updater(projectEntities)

    val changes = projectEntities.collectChanges(before)
    projectEntities.resetChanges()

    if (notify) {
      entityStorage.replace(projectEntities.toStorage(), changes)
    }
    else {
      (entityStorage as ProjectModelEntityStorage).replaceSilent(projectEntities.toStorage(), changes)
    }

    return result
  }

  override fun dispose() = Unit

  private class ProjectModelEntityStorage(private val project: Project, initialStorage: WorkspaceEntityStorage) : VersionedEntityStorageImpl(initialStorage) {

    private var notificationsEnabled = true

    fun replaceSilent(newStorage: WorkspaceEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      notificationsEnabled = false
      try {
        replace(newStorage, changes)
      } finally {
        notificationsEnabled = true
      }
    }

    override fun onBeforeChanged(before: WorkspaceEntityStorage, after: WorkspaceEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      if (project.isDisposed || Disposer.isDisposing(project) || !notificationsEnabled) return
      WorkspaceModelTopics.getInstance(project).syncPublisher(project.messageBus).beforeChanged(
        VersionedStorageChangedImpl(entityStorage = this, storageBefore = before, storageAfter = after, changes = changes)
      )
    }

    override fun onChanged(before: WorkspaceEntityStorage, after: WorkspaceEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      if (project.isDisposed || Disposer.isDisposing(project) || !notificationsEnabled) return
      WorkspaceModelTopics.getInstance(project).syncPublisher(project.messageBus).changed(
        VersionedStorageChangedImpl(entityStorage = this, storageBefore = before, storageAfter = after, changes = changes)
      )
    }
  }

  private class VersionedStorageChangedImpl(
    entityStorage: VersionedEntityStorage,
    override val storageBefore: WorkspaceEntityStorage,
    override val storageAfter: WorkspaceEntityStorage,
    private val changes: Map<Class<*>, List<EntityChange<*>>>) : VersionedStorageChanged(entityStorage) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>> =
      (changes[entityClass] as? List<EntityChange<T>>) ?: emptyList()

    override fun getAllChanges(): Sequence<EntityChange<*>> = changes.values.asSequence().flatten()
  }
}
