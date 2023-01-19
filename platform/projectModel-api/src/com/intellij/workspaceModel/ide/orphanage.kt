// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

object OrphanageWorkerEntitySource : EntitySource

class Orphanage(private val project: Project) {
  val entityStorage: VersionedEntityStorageImpl = VersionedEntityStorageImpl(EntityStorageSnapshot.empty())

  @RequiresWriteLock
  fun update(updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val before = entityStorage.current
    val builder = MutableEntityStorage.from(before)

    updater(builder)

    val changes = builder.collectChanges(before)

    checkIfParentsAlreadyExist(changes, builder)

    val newStorage: EntityStorageSnapshot = builder.toSnapshot()
    entityStorage.replace(newStorage, emptyMap(), {}, {})
  }

  private fun checkIfParentsAlreadyExist(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val addedModules = changes[ModuleEntity::class.java]
                         ?.filterIsInstance<EntityChange.Added<ModuleEntity>>()
                         ?.map { it.entity } ?: return

    val adders = listOf(
      ContentRootAdder(project),
      SourceRootAdder(project),
      ExcludeRootAdder(project),
    )

    adders.forEach { it.collectOrphanRoots(addedModules, false) }

    if (adders.any { it.anyUpdates() }) {
      project.workspaceModel.updateProjectModel("Apply orphan elements to already existing entities") { mutableBuilder ->
        adders.forEach { it.addToBuilder(mutableBuilder) }
      }
    }
    adders.forEach { it.cleanOrphanage(builder) }
  }

  companion object {
    val use: Boolean by lazy { Registry.`is`("ide.workspace.model.separate.component.for.roots", false) }
  }
}

class OrphanListener(val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {

    if (!Orphanage.use) return

    // Do not move to the field! They should be created every time! (or the code should be refactored)
    val adders = listOf(
      ContentRootAdder(project),
      SourceRootAdder(project),
      ExcludeRootAdder(project),
    )

    val changedModules = event.getChanges(ModuleEntity::class.java)
      .filterIsInstance<EntityChange.Added<ModuleEntity>>()
      .map { it.entity }

    adders.forEach { it.collectParentChanges(event) }

    if (adders.any { it.anyEntitiesToMove() }) {
      runLaterAndWrite {
        val orphanage = project.workspaceModel.orphanage.entityStorage.pointer.storage
        val orphanModules = changedModules.mapNotNull {
          orphanage.resolve(it.symbolicId)
        }
        adders.forEach { it.collectOrphanRoots(orphanModules, true) }

        if (adders.any { it.anyUpdates() }) {
          project.workspaceModel.updateProjectModel("Move orphan elements") { storage ->
            adders.forEach { it.addToBuilder(storage) }
          }
        }
        project.workspaceModel.orphanage.update {
          adders.forEach { adder -> adder.cleanOrphanage(it) }
        }
      }
    }
  }

  private inline fun runLaterAndWrite(crossinline run: () -> Unit) {
    // This is important to use invokeLater in order not to update the project model inside the listeners
    ApplicationManager.getApplication().invokeLater {
      ApplicationManager.getApplication().runWriteAction {
        run()
      }
    }
  }
}

//--- Helper classes

interface EntityAdder {
  fun collectParentChanges(event: VersionedStorageChange)
  fun anyEntitiesToMove(): Boolean
  fun collectOrphanRoots(orphanModules: List<ModuleEntity>, cleanDisappearedModules: Boolean)
  fun anyUpdates(): Boolean
  fun addToBuilder(builder: MutableEntityStorage)
  fun cleanOrphanage(builder: MutableEntityStorage)
}

private class ContentRootAdder(private val project: Project) : EntityAdder {
  lateinit var eventModules: List<ModuleEntity>
  val disappearedModules: MutableList<ModuleEntity> = ArrayList()

  private lateinit var updates: List<Pair<ModuleEntity, List<ContentRootEntity.Builder>>>
  private val entitiesToRemoveFromOrphanage = ArrayList<ContentRootEntity>()
  override fun collectParentChanges(event: VersionedStorageChange) {
    eventModules = event.getChanges(ModuleEntity::class.java)
      .filterIsInstance<EntityChange.Added<ModuleEntity>>()
      .map { it.entity }
  }

  override fun anyEntitiesToMove(): Boolean {
    val orphanageSnapshot = project.workspaceModel.orphanage.entityStorage.pointer.storage

    return eventModules.isNotEmpty() && eventModules.any { orphanageSnapshot.resolve(it.symbolicId) != null }
  }

  override fun collectOrphanRoots(orphanModules: List<ModuleEntity>, cleanDisappearedModules: Boolean) {
    val snapshot = project.workspaceModel.currentSnapshot
    updates = orphanModules.mapNotNull { orphanModule ->
      val snapshotModule = snapshot.resolve(orphanModule.symbolicId) ?: run {
        if (cleanDisappearedModules) {
          disappearedModules += orphanModule
        }
        return@mapNotNull null
      }

      val existingUrls = snapshotModule.contentRoots.mapTo(HashSet()) { it.url }
      val rootsToAdd = orphanModule.contentRoots
        .filter { it.entitySource !is OrphanageWorkerEntitySource }
        .onEach { entitiesToRemoveFromOrphanage += it }
        .filter { it.url !in existingUrls }
        .map { it.createEntityTreeCopy() as ContentRootEntity.Builder }

      if (rootsToAdd.isNotEmpty()) {
        snapshotModule to rootsToAdd
      }
      else null
    }
  }

  override fun anyUpdates(): Boolean {
    return updates.isNotEmpty()
  }

  override fun addToBuilder(builder: MutableEntityStorage) {
    updates.forEach { (snapshotModule, rootsToAdd) ->
      builder.modifyEntity(snapshotModule) {
        this.contentRoots += rootsToAdd
      }
    }
  }

  override fun cleanOrphanage(builder: MutableEntityStorage) {
    entitiesToRemoveFromOrphanage.forEach {
      val moduleId = it.module.symbolicId

      builder.removeEntity(it)

      val module = builder.resolve(moduleId) ?: return@forEach
      if (module.contentRoots.isEmpty()) {
        builder.removeEntity(module)
      }
    }


    disappearedModules
      .forEach { module ->
        val notedModule = builder.resolve(module.symbolicId) ?: return@forEach
        builder.removeEntity(notedModule)
      }
  }
}

private class SourceRootAdder(private val project: Project) : EntityAdder {
  lateinit var targetContentRoots: List<ContentRootEntity>

  lateinit var updates: List<Pair<ModuleEntity, List<Pair<VirtualFileUrl, List<SourceRootEntity.Builder>>>>>
  private val entitiesToRemoveFromOrphanage = ArrayList<SourceRootEntity>()
  val disappearedModules: MutableList<ModuleEntity> = ArrayList()

  override fun collectParentChanges(event: VersionedStorageChange) {
    targetContentRoots = event.getChanges(ContentRootEntity::class.java)
      .filterIsInstance<EntityChange.Added<ContentRootEntity>>()
      .map { it.entity }
  }

  override fun anyEntitiesToMove(): Boolean {
    val orphanageSnapshot = project.workspaceModel.orphanage.entityStorage.pointer.storage

    return targetContentRoots.isNotEmpty()
           && targetContentRoots.any {
      orphanageSnapshot.resolve(it.module.symbolicId)?.contentRoots?.any { root -> root.url == it.url } == true
    }
  }

  override fun collectOrphanRoots(orphanModules: List<ModuleEntity>, cleanDisappearedModules: Boolean) {
    updates = orphanModules.mapNotNull { orphanModule ->
      val snapshot = project.workspaceModel.currentSnapshot
      val snapshotModule = snapshot.resolve(orphanModule.symbolicId) ?: run {
        if (cleanDisappearedModules) {
          disappearedModules += orphanModule
        }
        return@mapNotNull null
      }

      val existingContentUrls = snapshotModule.contentRoots
        .associate { it.url to it.sourceRoots.mapTo(HashSet()) { it.url } }
      val rootsToAdd = orphanModule.contentRoots
        .filter { it.url in existingContentUrls && it.entitySource is OrphanageWorkerEntitySource }
        .onEach { entitiesToRemoveFromOrphanage += it.sourceRoots }
        .mapNotNull { contentRoot ->
          val sourcesToAdd = contentRoot.sourceRoots
            .filter { it.url !in (existingContentUrls[contentRoot.url] ?: emptyList()) }
            .map { it.createEntityTreeCopy() as SourceRootEntity.Builder }

          if (sourcesToAdd.isNotEmpty()) {
            contentRoot.url to sourcesToAdd
          }
          else null
        }

      if (rootsToAdd.isNotEmpty()) snapshotModule to rootsToAdd else null
    }
  }

  override fun anyUpdates(): Boolean {
    return updates.isNotEmpty()
  }

  override fun addToBuilder(builder: MutableEntityStorage) {
    updates.forEach { (snapshotModule, rootsToAdd) ->
      rootsToAdd.forEach { (root, sources) ->
        val contentRoot = snapshotModule.contentRoots.find { it.url == root }!!
        builder.modifyEntity(contentRoot) {
          this.sourceRoots += sources
        }
      }
    }
  }

  override fun cleanOrphanage(builder: MutableEntityStorage) {

    entitiesToRemoveFromOrphanage.forEach {
      val moduleId = it.contentRoot.module.symbolicId
      val contentUrl = it.contentRoot.url

      builder.removeEntity(it)

      val module = builder.resolve(moduleId) ?: return@forEach

      // TODO: Another o^2?
      val root = module.contentRoots.firstOrNull { it.url == contentUrl } ?: return@forEach
      if (root.sourceRoots.isEmpty() && root.excludedUrls.isEmpty()) {
        builder.removeEntity(root)

        val moduleAgain = builder.resolve(moduleId) ?: return@forEach
        if (moduleAgain.contentRoots.isEmpty()) {
          builder.removeEntity(moduleAgain)
        }
      }
    }

    disappearedModules
      .forEach { module ->
        val notedModule = builder.resolve(module.symbolicId) ?: return@forEach
        builder.removeEntity(notedModule)
      }
  }
}

private class ExcludeRootAdder(private val project: Project) : EntityAdder {
  lateinit var targetContentRoots: List<ContentRootEntity>

  lateinit var updates: List<Pair<ModuleEntity, List<Pair<VirtualFileUrl, List<ExcludeUrlEntity.Builder>>>>>
  private val entitiesToRemoveFromOrphanage = ArrayList<ExcludeUrlEntity>()
  val disappearedModules: MutableList<ModuleEntity> = ArrayList()

  override fun collectParentChanges(event: VersionedStorageChange) {
    targetContentRoots = event.getChanges(ContentRootEntity::class.java)
      .filterIsInstance<EntityChange.Added<ContentRootEntity>>()
      .map { it.entity }
  }

  override fun anyEntitiesToMove(): Boolean {
    val orphanageSnapshot = project.workspaceModel.orphanage.entityStorage.pointer.storage

    return targetContentRoots.isNotEmpty()
           && targetContentRoots.any {
      orphanageSnapshot.resolve(it.module.symbolicId)?.contentRoots?.any { root -> root.url == it.url } == true
    }
  }

  override fun collectOrphanRoots(orphanModules: List<ModuleEntity>, cleanDisappearedModules: Boolean) {
    updates = orphanModules.mapNotNull { orphanModule ->
      val snapshot = project.workspaceModel.currentSnapshot
      val snapshotModule = snapshot.resolve(orphanModule.symbolicId) ?: run {
        if (cleanDisappearedModules) {
          disappearedModules += orphanModule
        }
        return@mapNotNull null
      }

      val existingExcludes = snapshotModule.contentRoots
        .associate { it.url to it.excludedUrls.mapTo(HashSet()) { it.url } }
      val rootsToAdd = orphanModule.contentRoots
        .filter { it.url in existingExcludes && it.entitySource is OrphanageWorkerEntitySource }
        .onEach { entitiesToRemoveFromOrphanage += it.excludedUrls }
        .mapNotNull { contentRoot ->
          val excludeToAdd = contentRoot.excludedUrls
            .filter { it.url !in (existingExcludes[contentRoot.url] ?: emptyList()) }
            .map { it.createEntityTreeCopy() as ExcludeUrlEntity.Builder }

          if (excludeToAdd.isNotEmpty()) {
            contentRoot.url to excludeToAdd
          }
          else null
        }

      if (rootsToAdd.isNotEmpty()) snapshotModule to rootsToAdd else null
    }
  }

  override fun anyUpdates(): Boolean {
    return updates.isNotEmpty()
  }

  override fun addToBuilder(builder: MutableEntityStorage) {

    updates.forEach { (snapshotModule, rootsToAdd) ->
      rootsToAdd.forEach { (root, excludes) ->
        val contentRoot = snapshotModule.contentRoots.find { it.url == root }!!
        builder.modifyEntity(contentRoot) {
          this.excludedUrls += excludes
        }
      }
    }
  }

  override fun cleanOrphanage(builder: MutableEntityStorage) {

    entitiesToRemoveFromOrphanage.forEach {
      val moduleId = it.contentRoot!!.module.symbolicId
      val contentUrl = it.contentRoot!!.url

      builder.removeEntity(it)

      val module = builder.resolve(moduleId) ?: return@forEach

      // TODO: Another o^2?
      val root = module.contentRoots.firstOrNull { it.url == contentUrl } ?: return@forEach
      if (root.sourceRoots.isEmpty() && root.excludedUrls.isEmpty()) {
        builder.removeEntity(root)

        val moduleAgain = builder.resolve(moduleId) ?: return@forEach
        if (moduleAgain.contentRoots.isEmpty()) {
          builder.removeEntity(moduleAgain)
        }
      }
    }

    disappearedModules
      .forEach { module ->
        val notedModule = builder.resolve(module.symbolicId) ?: return@forEach
        builder.removeEntity(notedModule)
      }
  }
}
