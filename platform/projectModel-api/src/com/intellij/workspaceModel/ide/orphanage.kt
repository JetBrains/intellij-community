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
      ContentRootsAdder(),
      SourceRootsAdder(),
      ExcludesAdder(),
    )

    adders.forEach { it.collectUpdates(addedModules) }

    if (adders.any { it.anyUpdates() }) {
      project.workspaceModel.updateProjectModel("Apply orphan elements to already existing entities") { mutableBuilder ->
        adders.forEach { it.applyUpdates(mutableBuilder) }
      }
    }
    adders.forEach { it.cleanOrphanBuilder(builder) }
  }

  interface Adder {
    fun collectUpdates(addedModules: List<ModuleEntity>)
    fun anyUpdates(): Boolean
    fun applyUpdates(builder: MutableEntityStorage)
    fun cleanOrphanBuilder(builder: MutableEntityStorage)
  }

  inner class ExcludesAdder : Adder {
    private val entitiesToRemoveFromOrphanage = ArrayList<ExcludeUrlEntity>()
    lateinit var updates: List<Pair<ModuleEntity, List<Pair<VirtualFileUrl, List<ExcludeUrlEntity.Builder>>>>>
    override fun collectUpdates(addedModules: List<ModuleEntity>) {
      updates = addedModules.mapNotNull { orphanModule ->
        val snapshot = project.workspaceModel.currentSnapshot
        val snapshotModule = snapshot.resolve(orphanModule.symbolicId) ?: return@mapNotNull null

        val existingContentUrls = snapshotModule.contentRoots
          .associate { content -> content.url to content.excludedUrls.mapTo(HashSet()) { it.url } }
        val rootsToAdd = orphanModule.contentRoots
          .filter { it.url in existingContentUrls && it.entitySource is OrphanageWorkerEntitySource }
          .onEach { entitiesToRemoveFromOrphanage += it.excludedUrls }
          .mapNotNull { contentRoot ->
            val excludesUrl = contentRoot.excludedUrls
              .filter { it.url !in (existingContentUrls[contentRoot.url] ?: emptyList()) }
              .map { it.createEntityTreeCopy() as ExcludeUrlEntity.Builder }

            if (excludesUrl.isNotEmpty()) {
              contentRoot.url to excludesUrl
            } else null
          }

        if (rootsToAdd.isNotEmpty()) snapshotModule to rootsToAdd else null
      }
    }

    override fun anyUpdates(): Boolean {
      return updates.isNotEmpty()
    }

    override fun applyUpdates(builder: MutableEntityStorage) {
      // TODO: Looks like o^2
      updates.forEach { (snapshotModule, data) ->
        data.forEach inner@ { (rootUrl, newExcludes) ->
          val contentRoot = snapshotModule.contentRoots.firstOrNull { it.url == rootUrl } ?: return@inner
          builder.modifyEntity(contentRoot) {
            this.excludedUrls += newExcludes
          }
        }
      }
    }

    override fun cleanOrphanBuilder(builder: MutableEntityStorage) {
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
    }
  }

  inner class SourceRootsAdder : Adder {
    private val entitiesToRemoveFromOrphanage = ArrayList<SourceRootEntity>()
    lateinit var updates: List<Pair<ModuleEntity, List<Pair<VirtualFileUrl, List<SourceRootEntity.Builder>>>>>
    override fun collectUpdates(addedModules: List<ModuleEntity>) {
      updates = addedModules.mapNotNull { orphanModule ->
        val snapshot = project.workspaceModel.currentSnapshot
        val snapshotModule = snapshot.resolve(orphanModule.symbolicId) ?: return@mapNotNull null

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
            } else null
          }

        if (rootsToAdd.isNotEmpty()) snapshotModule to rootsToAdd else null
      }
    }

    override fun anyUpdates(): Boolean {
      return updates.isNotEmpty()
    }

    override fun applyUpdates(builder: MutableEntityStorage) {
      // TODO: Looks like o^2
      updates.forEach { (snapshotModule, data) ->
        data.forEach inner@ { (rootUrl, newSources) ->
          val contentRoot = snapshotModule.contentRoots.firstOrNull { it.url == rootUrl } ?: return@inner
          builder.modifyEntity(contentRoot) {
            this.sourceRoots += newSources
          }
        }
      }
    }

    override fun cleanOrphanBuilder(builder: MutableEntityStorage) {
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
    }
  }

  inner class ContentRootsAdder : Adder {
    private lateinit var updates: List<Pair<ModuleEntity, List<ContentRootEntity.Builder>>>
    private val entitiesToRemoveFromOrphanage = ArrayList<ContentRootEntity>()

    override fun collectUpdates(addedModules: List<ModuleEntity>) {
      updates = addedModules.mapNotNull { orphanModule ->
        val snapshot = project.workspaceModel.currentSnapshot
        val snapshotModule = snapshot.resolve(orphanModule.symbolicId) ?: return@mapNotNull null

        val existingUrls = snapshotModule.contentRoots.mapTo(HashSet()) { it.url }
        val rootsToAdd = orphanModule.contentRoots
          .filter { it.entitySource !is OrphanageWorkerEntitySource }
          .onEach { entitiesToRemoveFromOrphanage += it }
          .filter { it.url !in existingUrls } // TODO: Test this! Existing url is removed from orphanage
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

    override fun applyUpdates(builder: MutableEntityStorage) {
      updates.forEach { (snapshotModule, rootsToAdd) ->
        builder.modifyEntity(snapshotModule) {
          this.contentRoots += rootsToAdd
        }
      }
    }

    override fun cleanOrphanBuilder(builder: MutableEntityStorage) {
      entitiesToRemoveFromOrphanage.forEach {
        val moduleId = it.module.symbolicId

        builder.removeEntity(it)

        val module = builder.resolve(moduleId) ?: return@forEach
        if (module.contentRoots.isEmpty()) {
          builder.removeEntity(module)
        }
      }
    }
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
      ContentRootAdder(),
      SourceRootAdder(),
      ExcludeAdder(),
    )

    adders.forEach { it.collectParentChanges(event) }

    if (adders.any { it.anyEntitiesToMove() }) {
      runLaterAndWrite {
        adders.forEach { it.collectOrphanRoots() }

        if (adders.any { it.anyOrphanRoots() }) {
          project.workspaceModel.updateProjectModel("Move orphan elements") { storage ->
            adders.forEach { it.addToBuilder(storage) }
          }

          adders.forEach { it.cleanOrphanage() }
        }
      }
    }
  }

  interface EntitiesAdder {
    fun collectParentChanges(event: VersionedStorageChange)
    fun anyEntitiesToMove(): Boolean
    fun collectOrphanRoots()
    fun anyOrphanRoots(): Boolean
    fun addToBuilder(builder: MutableEntityStorage)
    fun cleanOrphanage()
  }

  private inner class ContentRootAdder : EntitiesAdder {
    lateinit var targetModules: List<ModuleEntity>
    lateinit var orphanageRoots: Map<ModuleEntity, List<ContentRootEntity.Builder>>
    override fun collectParentChanges(event: VersionedStorageChange) {
      targetModules = event.getChanges(ModuleEntity::class.java)
        .filterIsInstance<EntityChange.Added<ModuleEntity>>()
        .map { it.entity }
    }

    override fun anyEntitiesToMove(): Boolean {
      val orphanageSnapshot = project.workspaceModel.orphanage.entityStorage.pointer.storage

      return targetModules.isNotEmpty() && targetModules.any { orphanageSnapshot.resolve(it.symbolicId) != null }
    }

    override fun collectOrphanRoots() {
      val orphanage = project.workspaceModel.orphanage.entityStorage.pointer.storage
      orphanageRoots = targetModules
        .mapNotNull { orphanage.resolve(it.symbolicId) }
        .filter { module -> module.contentRoots.filterNot { it.entitySource is OrphanageWorkerEntitySource }.isNotEmpty() }
        .associateWith { module -> module.contentRoots.filterNot { it.entitySource is OrphanageWorkerEntitySource }.map { root -> root.createEntityTreeCopy() as ContentRootEntity.Builder } }
    }

    override fun anyOrphanRoots(): Boolean {
      return orphanageRoots.isNotEmpty()
    }

    override fun addToBuilder(builder: MutableEntityStorage) {
      orphanageRoots.forEach { (module, roots) ->
        val localModule = builder.resolve(module.symbolicId) ?: return@forEach
        val existingUrls = localModule.contentRoots.mapTo(HashSet()) { it.url }
        val rootsToAdd = roots.filter { it.url !in existingUrls }
        if (rootsToAdd.isNotEmpty()) {
          builder.modifyEntity(localModule) {
            this.contentRoots += rootsToAdd
          }
        }
      }
    }

    override fun cleanOrphanage() {
      project.workspaceModel.orphanage.update {
        orphanageRoots.forEach { (module, roots) ->
          val rootSet = roots.mapTo(HashSet()) { it.url }
          val orphanModule = it.resolve(module.symbolicId) ?: return@forEach
          orphanModule.contentRoots.filter { it.url in rootSet }
            .forEach { toRemove -> it.removeEntity(toRemove) }

          it.resolve(module.symbolicId)?.let { moduleEntity ->
            if (moduleEntity.contentRoots.isEmpty()) {
              it.removeEntity(moduleEntity)
            }
          }
        }
      }
    }
  }

  private inner class SourceRootAdder : EntitiesAdder {
    lateinit var targetContentRoots: List<ContentRootEntity>
    lateinit var orphanRoots: Map<ContentRootEntity, List<SourceRootEntity.Builder>>

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

    override fun collectOrphanRoots() {
      val orphanage = project.workspaceModel.orphanage.entityStorage.pointer.storage
      orphanRoots = targetContentRoots
        .mapNotNull { root -> orphanage.resolve(root.module.symbolicId)?.contentRoots?.firstOrNull { it.url == root.url } }
        .filter { it.sourceRoots.isNotEmpty() }
        .associateWith { it.sourceRoots.map { source -> source.createEntityTreeCopy() as SourceRootEntity.Builder } }
    }

    override fun anyOrphanRoots(): Boolean {
      return orphanRoots.isNotEmpty()
    }

    override fun addToBuilder(builder: MutableEntityStorage) {
      orphanRoots.forEach { (contentRoot, sources) ->
        val localRoot = builder.resolve(contentRoot.module.symbolicId)?.contentRoots?.firstOrNull { it.url == contentRoot.url }
                        ?: return@forEach
        val existingUrls = localRoot.sourceRoots.mapTo(HashSet()) { it.url }
        val sourcesToAdd = sources.filter { it.url !in existingUrls }
        if (sourcesToAdd.isNotEmpty()) {
          builder.modifyEntity(localRoot) {
            this.sourceRoots += sourcesToAdd
          }
        }
      }
    }

    override fun cleanOrphanage() {
      project.workspaceModel.orphanage.update {
        orphanRoots.forEach { (contentRoot, sources) ->
          val sourceSet = sources.mapTo(HashSet()) { it.url }
          it.resolve(contentRoot.module.symbolicId)
            ?.contentRoots
            ?.firstOrNull { it.url == contentRoot.url }
            ?.sourceRoots
            ?.filter { it.url in sourceSet }
            ?.forEach { toRemove -> it.removeEntity(toRemove) }

          it.resolve(contentRoot.module.symbolicId)?.contentRoots?.firstOrNull { it.url == contentRoot.url }?.let { orphanRoot ->
            if (orphanRoot.excludedUrls.isEmpty() && orphanRoot.excludedUrls.isEmpty()) {
              it.removeEntity(orphanRoot)
            }
          }

          it.resolve(contentRoot.module.symbolicId)?.let { moduleEntity ->
            if (moduleEntity.contentRoots.isEmpty()) {
              it.removeEntity(moduleEntity)
            }
          }
        }
      }
    }
  }

  private inner class ExcludeAdder : EntitiesAdder {
    lateinit var targetContentRoots: List<ContentRootEntity>
    lateinit var orphanRoots: Map<ContentRootEntity, List<ExcludeUrlEntity.Builder>>

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

    override fun collectOrphanRoots() {
      val orphanage = project.workspaceModel.orphanage.entityStorage.pointer.storage
      orphanRoots = targetContentRoots
        .mapNotNull { root -> orphanage.resolve(root.module.symbolicId)?.contentRoots?.firstOrNull { it.url == root.url } }
        .filter { it.excludedUrls.isNotEmpty() }
        .associateWith { it.excludedUrls.map { exclude -> exclude.createEntityTreeCopy() as ExcludeUrlEntity.Builder } }
    }

    override fun anyOrphanRoots(): Boolean {
      return orphanRoots.isNotEmpty()
    }

    override fun addToBuilder(builder: MutableEntityStorage) {
      orphanRoots.forEach { (contentRoot, excludes) ->
        val localRoot = builder.resolve(contentRoot.module.symbolicId)?.contentRoots?.firstOrNull { it.url == contentRoot.url }
                        ?: return@forEach
        val existingUrls = localRoot.excludedUrls.mapTo(HashSet()) { it.url }
        val excludesToAdd = excludes.filter { it.url !in existingUrls }
        if (excludesToAdd.isNotEmpty()) {
          builder.modifyEntity(localRoot) {
            this.excludedUrls += excludesToAdd
          }
        }
      }
    }

    override fun cleanOrphanage() {
      project.workspaceModel.orphanage.update {
        orphanRoots.forEach { (contentRoot, excludes) ->
          val excludesSet = excludes.mapTo(HashSet()) { it.url }
          it.resolve(contentRoot.module.symbolicId)
            ?.contentRoots
            ?.firstOrNull { it.url == contentRoot.url }
            ?.excludedUrls
            ?.filter { it.url in excludesSet }
            ?.forEach { toRemove -> it.removeEntity(toRemove) }

          it.resolve(contentRoot.module.symbolicId)?.contentRoots?.firstOrNull { it.url == contentRoot.url }?.let { orphanRoot ->
            if (orphanRoot.excludedUrls.isEmpty() && orphanRoot.excludedUrls.isEmpty()) {
              it.removeEntity(orphanRoot)
            }
          }

          it.resolve(contentRoot.module.symbolicId)?.let { moduleEntity ->
            if (moduleEntity.contentRoots.isEmpty()) {
              it.removeEntity(moduleEntity)
            }
          }
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

object OrphanageWorkerEntitySource : EntitySource
