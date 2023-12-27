// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

class EntitiesOrphanageImpl(private val project: Project) : EntitiesOrphanage {
  private val entityStorage: VersionedEntityStorageImpl = VersionedEntityStorageImpl(EntityStorageSnapshot.empty())
  override val currentSnapshot: EntityStorageSnapshot
    get() = entityStorage.current

  @OptIn(EntityStorageInstrumentationApi::class)
  @RequiresWriteLock
  override fun update(updater: (MutableEntityStorage) -> Unit) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    val before = entityStorage.current
    val builder = MutableEntityStorage.from(before)

    updater(builder)

    val changes = (builder as MutableEntityStorageInstrumentation).collectChanges()

    checkIfParentsAlreadyExist(changes, builder)

    val newStorage: EntityStorageSnapshot = builder.toSnapshot()
    entityStorage.replace(newStorage, emptyMap(), {}, {})

    log.info("Update orphanage. ${changes[ModuleEntity::class.java]?.size ?: 0} modules added")
  }

  private fun checkIfParentsAlreadyExist(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val orphanModules = changes[ModuleEntity::class.java]
                          ?.filterIsInstance<EntityChange.Added<ModuleEntity>>()
                          ?.map { it.entity } ?: return

    val snapshot = project.workspaceModel.currentSnapshot
    val orphanToSnapshotModule = orphanModules
      .mapNotNull { snapshot.resolve(it.symbolicId)?.let { sm -> it to sm } }

    val adders = listOf(
      ContentRootAdder(),
      SourceRootAdder(),
      ExcludeRootAdder(),
    )

    adders.forEach { it.collectOrphanRoots(orphanToSnapshotModule) }

    if (adders.any { it.hasUpdates() }) {
      project.workspaceModel.updateProjectModel("Apply orphan elements to already existing entities") { mutableBuilder ->
        adders.forEach { it.addToBuilder(mutableBuilder) }
      }
    }
    adders.forEach { it.cleanOrphanage(builder) }
  }

  companion object {
    private val log: Logger = logger<EntitiesOrphanageImpl>()
  }
}

class OrphanListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    if (!EntitiesOrphanage.isEnabled) return

    val adders = listOf(
      ContentRootAdder(),
      SourceRootAdder(),
      ExcludeRootAdder(),
    )

    val updateTime = measureTimeMillis {
      // Do not move to the field! They should be created every time! (or the code should be refactored)
      val changedModules = event.getChanges(ModuleEntity::class.java)
        .filterIsInstance<EntityChange.Added<ModuleEntity>>()
        .map { it.entity }

      val orphanage = EntitiesOrphanage.getInstance(project).currentSnapshot
      val orphanModules = changedModules.mapNotNull {
        orphanage.resolve(it.symbolicId)?.let { om -> om to it }
      }
      adders.forEach { it.collectOrphanRoots(orphanModules) }

      EntitiesOrphanage.getInstance(project).update {
        adders.forEach { adder -> adder.cleanOrphanage(it) }
      }
      if (adders.any { it.hasUpdates() }) {
        project.workspaceModel.updateProjectModel("Move orphan elements") { storage ->
          adders.forEach { it.addToBuilder(storage) }
        }
      }
    }
    updateOrphanTimeMs.addAndGet(updateTime)
    if (updateTime > 1_000) log.warn("Orphanage update took $updateTime ms")
  }

  companion object {
    private val log = logger<OrphanListener>()

    private val updateOrphanTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter) {
      val updateOrphanTimeCounter = meter.counterBuilder("workspaceModel.orphan.listener.update.ms").buildObserver()

      meter.batchCallback({ updateOrphanTimeCounter.record(updateOrphanTimeMs.get()) }, updateOrphanTimeCounter)
    }

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}

//--- Helper classes

/**
 * Stateful helper classes to update storage.
 * They may be refactored to remove the state and use them as extension points, if this will be needed
 */
private interface EntityAdder {
  fun collectOrphanRoots(orphanToSnapshotModules: List<Pair<ModuleEntity, ModuleEntity>>)
  fun hasUpdates(): Boolean
  fun addToBuilder(builder: MutableEntityStorage)
  fun cleanOrphanage(builder: MutableEntityStorage)
}

private class ContentRootAdder : EntityAdder {
  private lateinit var updates: List<Pair<ModuleEntity, List<ContentRootEntity.Builder>>>
  private val entitiesToRemoveFromOrphanage = ArrayList<ContentRootEntity>()

  override fun collectOrphanRoots(orphanToSnapshotModules: List<Pair<ModuleEntity, ModuleEntity>>) {
    updates = orphanToSnapshotModules.mapNotNull { (orphanModule, snapshotModule) ->
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

  override fun hasUpdates(): Boolean {
    return updates.isNotEmpty()
  }

  override fun addToBuilder(builder: MutableEntityStorage) {
    log.info("Move content roots for ${updates.size} modules from orphanage to storage")
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
  }

  companion object {
    val log = logger<ContentRootAdder>()
  }
}

private class SourceRootAdder : EntityAdder {
  lateinit var updates: List<Pair<ModuleEntity, List<Pair<VirtualFileUrl, List<SourceRootEntity.Builder>>>>>
  private val entitiesToRemoveFromOrphanage = ArrayList<SourceRootEntity>()

  override fun collectOrphanRoots(orphanToSnapshotModules: List<Pair<ModuleEntity, ModuleEntity>>) {
    updates = orphanToSnapshotModules.mapNotNull { (orphanModule, snapshotModule) ->
      val existingContentUrls = snapshotModule.contentRoots
        .associate { contentRoot -> contentRoot.url to contentRoot.sourceRoots.mapTo(HashSet()) { it.url } }
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

  override fun hasUpdates(): Boolean {
    return updates.isNotEmpty()
  }

  @Suppress("DuplicatedCode")
  override fun addToBuilder(builder: MutableEntityStorage) {
    log.info("Move source roots for ${updates.size} modules from orphanage to storage")
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
      // This should be done before remove
      val contentRootReference = it.contentRoot.createReference<ContentRootEntity>()
      val moduleReference = it.contentRoot.module.createReference<ModuleEntity>()

      builder.removeEntity(it)

      val content = contentRootReference.resolve(builder) ?: return@forEach
      if (content.sourceRoots.isEmpty() && content.excludedUrls.isEmpty()) {
        builder.removeEntity(content)

        val module = moduleReference.resolve(builder) ?: return@forEach
        if (module.contentRoots.isEmpty()) {
          builder.removeEntity(module)
        }
      }
    }
  }

  companion object {
    val log = logger<SourceRootAdder>()
  }
}

private class ExcludeRootAdder : EntityAdder {
  lateinit var updates: List<Pair<ModuleEntity, List<Pair<VirtualFileUrl, List<ExcludeUrlEntity.Builder>>>>>
  private val entitiesToRemoveFromOrphanage = ArrayList<ExcludeUrlEntity>()

  override fun collectOrphanRoots(orphanToSnapshotModules: List<Pair<ModuleEntity, ModuleEntity>>) {
    updates = orphanToSnapshotModules.mapNotNull { (orphanModule, snapshotModule) ->
      val existingExcludes = snapshotModule.contentRoots
        .associate { contentRoot -> contentRoot.url to contentRoot.excludedUrls.mapTo(HashSet()) { it.url } }
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

  override fun hasUpdates(): Boolean {
    return updates.isNotEmpty()
  }

  @Suppress("DuplicatedCode")
  override fun addToBuilder(builder: MutableEntityStorage) {
    log.info("Move exclude roots for ${updates.size} modules from orphanage to storage")
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
      // This should be done before removing
      val moduleReference = it.contentRoot?.module?.createReference<ModuleEntity>()
      val contentReference = it.contentRoot?.createReference<ContentRootEntity>()

      builder.removeEntity(it)

      val content = contentReference?.resolve(builder) ?: return@forEach
      if (content.excludedUrls.isEmpty() && content.sourceRoots.isEmpty()) {
        builder.removeEntity(content)

        val module = moduleReference?.resolve(builder) ?: return@forEach
        if (module.contentRoots.isEmpty()) {
          builder.removeEntity(module)
        }
      }
    }
  }

  companion object {
    val log = logger<ExcludeRootAdder>()
  }
}
