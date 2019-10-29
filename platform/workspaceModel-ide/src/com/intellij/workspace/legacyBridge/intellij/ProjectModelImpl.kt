package com.intellij.workspace.legacyBridge.intellij

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.workspace.api.*

class ProjectModelImpl(project: Project): ProjectModel, Disposable {

  private val projectEntities: TypedEntityStorageBuilder

  private val cacheEnabled = !ApplicationManager.getApplication().isUnitTestMode
  private val cache = if (cacheEnabled) ProjectModelCacheImpl(project, this) else null

  override val entityStore: EntityStoreImpl

  init {
    // TODO It's possible to load this cache from the moment we know project path
    //  Like in ProjectLifecycleListener or something

    val initialContent = ProjectModelInitialTestContent.pop()
    if (initialContent != null) {
      projectEntities = TypedEntityStorageBuilder.from(initialContent)
    } else if (cache != null) {
      val previousStorage = cache.loadCache()
      projectEntities = if (previousStorage != null) TypedEntityStorageBuilder.from(previousStorage) else TypedEntityStorageBuilder.create()
    } else {
      projectEntities = TypedEntityStorageBuilder.create()
    }

    entityStore = ProjectModelEntityStore(project, projectEntities.toStorage())
  }

  // TODO We need transaction semantics here, failed updates should not poison everything
  override fun <R> updateProjectModel(updater: (TypedEntityStorageBuilder) -> R): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val before = projectEntities.toStorage()

    val result = updater(projectEntities)

    val changes = projectEntities.collectChanges(before)
    projectEntities.resetChanges()

    entityStore.replace(projectEntities.toStorage(), changes)

    return result
  }

  override fun dispose() = Unit

  private class ProjectModelEntityStore(private val project: Project, initialStorage: TypedEntityStorage)
    : EntityStoreImpl(initialStorage) {
    override fun onBeforeChanged(before: TypedEntityStorage, after: TypedEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      project.messageBus.syncPublisher(ProjectModelTopics.CHANGED).beforeChanged(
        EntityStoreChangedImpl(entityStore = this, storageBefore = before, storageAfter = after, changes = changes)
      )
    }

    override fun onChanged(before: TypedEntityStorage, after: TypedEntityStorage, changes: Map<Class<*>, List<EntityChange<*>>>) {
      project.messageBus.syncPublisher(ProjectModelTopics.CHANGED).changed(
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