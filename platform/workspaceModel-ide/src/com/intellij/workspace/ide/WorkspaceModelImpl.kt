// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspace.ide

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.workspace.api.*
import com.intellij.workspace.legacyBridge.intellij.LegacyBridgeProjectLifecycleListener

class WorkspaceModelImpl(project: Project): WorkspaceModel, Disposable {

  private val projectEntities: TypedEntityStorageBuilder

  private val cacheEnabled = !ApplicationManager.getApplication().isUnitTestMode && LegacyBridgeProjectLifecycleListener.cacheEnabled
  private val cache = if (cacheEnabled) WorkspaceModelCacheImpl(project, this) else null

  override val entityStore: EntityStoreImpl

  init {
    // TODO It's possible to load this cache from the moment we know project path
    //  Like in ProjectLifecycleListener or something

    val initialContent = WorkspaceModelInitialTestContent.pop()
    if (initialContent != null) {
      projectEntities = TypedEntityStorageBuilder.from(initialContent)
    } else if (cache != null) {
      val activity = StartUpMeasurer.startActivity("(wm) Loading cache")
      val previousStorage = cache.loadCache()
      projectEntities = if (previousStorage != null) TypedEntityStorageBuilder.from(previousStorage) else TypedEntityStorageBuilder.create()
      activity.end()
    } else {
      projectEntities = TypedEntityStorageBuilder.create()
    }

    entityStore = ProjectModelEntityStore(project, projectEntities.toStorage())
  }

  override fun <R> updateProjectModel(updater: (TypedEntityStorageBuilder) -> R): R = doUpdateProject(updater, true)

  override fun <R> updateProjectModelSilent(updater: (TypedEntityStorageBuilder) -> R): R = doUpdateProject(updater, false)

  // TODO We need transaction semantics here, failed updates should not poison everything
  private fun <R> doUpdateProject(updater: (TypedEntityStorageBuilder) -> R, notify: Boolean): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val before = projectEntities.toStorage()

    val result = updater(projectEntities)

    val changes = projectEntities.collectChanges(before)
    projectEntities.resetChanges()

    if (notify) {
      entityStore.replace(projectEntities.toStorage(), changes)
    }
    else {
      (entityStore as ProjectModelEntityStore).replaceSilent(projectEntities.toStorage(), changes)
    }

    return result
  }

  override fun dispose() = Unit

  private class ProjectModelEntityStore(private val project: Project, initialStorage: TypedEntityStorage) : EntityStoreImpl(initialStorage) {

    private var notificationsEnabled = true

    fun replaceSilent(newStorage: TypedEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      notificationsEnabled = false
      try {
        replace(newStorage, changes)
      } finally {
        notificationsEnabled = true
      }
    }

    override fun onBeforeChanged(before: TypedEntityStorage, after: TypedEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      if (project.isDisposed || Disposer.isDisposing(project) || !notificationsEnabled) return
      WorkspaceModelTopics.getInstance(project).syncPublisher(project.messageBus).beforeChanged(
        EntityStoreChangedImpl(entityStore = this, storageBefore = before, storageAfter = after, changes = changes)
      )
    }

    override fun onChanged(before: TypedEntityStorage, after: TypedEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      if (project.isDisposed || Disposer.isDisposing(project) || !notificationsEnabled) return
      WorkspaceModelTopics.getInstance(project).syncPublisher(project.messageBus).changed(
        EntityStoreChangedImpl(entityStore = this, storageBefore = before, storageAfter = after, changes = changes)
      )
    }
  }

  private class EntityStoreChangedImpl(
    entityStore: TypedEntityStore,
    override val storageBefore: TypedEntityStorage,
    override val storageAfter: TypedEntityStorage,
    private val changes: Map<Class<*>, List<EntityChange<*>>>) : EntityStoreChanged(entityStore) {

    @Suppress("UNCHECKED_CAST")
    override fun <T : TypedEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>> =
      (changes[entityClass] as? List<EntityChange<T>>) ?: emptyList()

    override fun getAllChanges(): Sequence<EntityChange<*>> = changes.values.asSequence().flatten()
  }
}