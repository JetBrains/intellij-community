// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.useReactiveWorkspaceModelApi
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.OrphanageWorkerEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.impl.VersionedEntityStorageImpl
import com.intellij.platform.workspace.storage.instrumentation.EntityStorageInstrumentationApi
import com.intellij.platform.workspace.storage.instrumentation.MutableEntityStorageInstrumentation
import com.intellij.platform.workspace.storage.query.entities
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.ide.EntitiesOrphanage
import io.opentelemetry.api.metrics.Meter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

private class OrphanageActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    if (useReactiveWorkspaceModelApi()) {
      setupOpenTelemetryReporting(jpsMetrics.meter)
      project.serviceAsync<OrphanService>().start()
    }
  }
}

internal class EntitiesOrphanageImpl(private val project: Project) : EntitiesOrphanage {
  private val entityStorage: VersionedEntityStorageImpl = VersionedEntityStorageImpl(ImmutableEntityStorage.empty())
  override val currentSnapshot: ImmutableEntityStorage
    get() = entityStorage.current

  @OptIn(EntityStorageInstrumentationApi::class)
  @RequiresWriteLock
  override fun update(updater: (MutableEntityStorage) -> Unit) {
    ThreadingAssertions.assertWriteAccess()

    val before = entityStorage.current
    val builder = MutableEntityStorage.from(before)

    updater(builder)

    val changes = (builder as MutableEntityStorageInstrumentation).collectChanges()

    checkIfParentsAlreadyExist(changes, builder)

    val newStorage: ImmutableEntityStorage = builder.toSnapshot()
    entityStorage.replace(newStorage, emptyMap(), {}, {})

    log.info("Update orphanage. ${changes[ModuleEntity::class.java]?.size ?: 0} modules added")
  }

  private fun checkIfParentsAlreadyExist(changes: Map<Class<*>, List<EntityChange<*>>>, builder: MutableEntityStorage) {
    val orphanModules = changes[ModuleEntity::class.java]
                          ?.filterIsInstance<EntityChange.Added<ModuleEntity>>()
                          ?.map { it.newEntity } ?: return

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

@Service(Service.Level.PROJECT)
internal class OrphanService(
  private val project: Project,
  private val cs: CoroutineScope,
) {

  private val query = entities<ModuleEntity>()

  fun start() {
    cs.launch {
      // flowOfDiff is used to process updates in batches
      (project.workspaceModel as WorkspaceModelInternal).flowOfDiff(query).collect { diff ->
        val added = diff.added

        val updateTime = measureTimeMillis {
          // Do not move to the field! They should be created every time! (or the code should be refactored)
          val adders = listOf(
            ContentRootAdder(),
            SourceRootAdder(),
            ExcludeRootAdder(),
          )

          val orphanage = EntitiesOrphanage.getInstance(project).currentSnapshot
          val orphanModules = added.mapNotNull {
            orphanage.resolve(it.symbolicId)?.let { om -> om to it }
          }
          adders.forEach { it.collectOrphanRoots(orphanModules) }

          val needToUpdateOrphanage = adders.any { it.hasUpdatesForOrphanage() }
          val needToUpdateWorkspaceModel = adders.any { it.hasUpdates() }
          if (needToUpdateWorkspaceModel || needToUpdateOrphanage) {
            edtWriteAction {
              if (needToUpdateOrphanage) {
                EntitiesOrphanage.getInstance(project).update {
                  adders.forEach { adder -> adder.cleanOrphanage(it) }
                }
              }
              if (needToUpdateWorkspaceModel) {
                project.workspaceModel.updateProjectModel("Move orphan elements") { storage ->
                  adders.forEach { it.addToBuilder(storage) }
                }
              }
            }
          }
        }

        updateOrphanTimeMs.duration.addAndGet(updateTime)
        if (updateTime > 1_000) LOG.warn("Orphanage update took $updateTime ms")
      }
    }
  }

  companion object {
    private val LOG = logger<OrphanService>()
  }
}

private class OrphanListener(private val project: Project) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    if (useReactiveWorkspaceModelApi()) return

    val adders = listOf(
      ContentRootAdder(),
      SourceRootAdder(),
      ExcludeRootAdder(),
    )

    val updateTime = measureTimeMillis {
      // Do not move to the field! They should be created every time! (or the code should be refactored)
      val changedModules = event.getChanges(ModuleEntity::class.java)
        .filterIsInstance<EntityChange.Added<ModuleEntity>>()
        .map { it.newEntity }

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
    updateOrphanTimeMs.duration.addAndGet(updateTime)
    if (updateTime > 1_000) log.warn("Orphanage update took $updateTime ms")
  }

  companion object {
    private val log = logger<OrphanListener>()

    init {
      setupOpenTelemetryReporting(jpsMetrics.meter)
    }
  }
}


private val updateOrphanTimeMs = MillisecondsMeasurer()

private fun setupOpenTelemetryReporting(meter: Meter) {
  val updateOrphanTimeCounter = meter.counterBuilder("workspaceModel.orphan.listener.update.ms").buildObserver()

  meter.batchCallback({ updateOrphanTimeCounter.record(updateOrphanTimeMs.asMilliseconds()) }, updateOrphanTimeCounter)
}

//--- Helper classes

/**
 * Stateful helper classes to update storage.
 * They may be refactored to remove the state and use them as extension points, if this will be needed
 */
private interface EntityAdder {
  fun collectOrphanRoots(orphanToSnapshotModules: List<Pair<ModuleEntity, ModuleEntity>>)
  fun hasUpdates(): Boolean
  fun hasUpdatesForOrphanage(): Boolean
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

  override fun hasUpdatesForOrphanage(): Boolean {
    return entitiesToRemoveFromOrphanage.isNotEmpty()
  }

  override fun addToBuilder(builder: MutableEntityStorage) {
    log.info("Move content roots for ${updates.size} modules from orphanage to storage")
    updates.forEach { (snapshotModule, rootsToAdd) ->
      val resolvedModule = builder.resolve(snapshotModule.symbolicId) ?: return@forEach
      builder.modifyModuleEntity(resolvedModule) {
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

  override fun hasUpdatesForOrphanage(): Boolean {
    return entitiesToRemoveFromOrphanage.isNotEmpty()
  }

  @Suppress("DuplicatedCode")
  override fun addToBuilder(builder: MutableEntityStorage) {
    log.info("Move source roots for ${updates.size} modules from orphanage to storage")
    updates.forEach { (snapshotModule, rootsToAdd) ->
      val resolvedModule = builder.resolve(snapshotModule.symbolicId) ?: return@forEach
      rootsToAdd.forEach { (root, sources) ->
        val contentRoot = resolvedModule.contentRoots.find { it.url == root }!!
        builder.modifyContentRootEntity(contentRoot) {
          this.sourceRoots += sources
        }
      }
    }
  }

  override fun cleanOrphanage(builder: MutableEntityStorage) {

    entitiesToRemoveFromOrphanage.forEach {
      // This should be done before remove
      val contentRootPointer = it.contentRoot.createPointer<ContentRootEntity>()
      val modulePointer = it.contentRoot.module.createPointer<ModuleEntity>()

      builder.removeEntity(it)

      val content = contentRootPointer.resolve(builder) ?: return@forEach
      if (content.sourceRoots.isEmpty() && content.excludedUrls.isEmpty()) {
        builder.removeEntity(content)

        val module = modulePointer.resolve(builder) ?: return@forEach
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

  override fun hasUpdatesForOrphanage(): Boolean {
    return entitiesToRemoveFromOrphanage.isNotEmpty()
  }

  @Suppress("DuplicatedCode")
  override fun addToBuilder(builder: MutableEntityStorage) {
    log.info("Move exclude roots for ${updates.size} modules from orphanage to storage")
    updates.forEach { (snapshotModule, rootsToAdd) ->
      val resolvedModule = builder.resolve(snapshotModule.symbolicId) ?: return@forEach
      rootsToAdd.forEach { (root, excludes) ->
        val contentRoot = resolvedModule.contentRoots.find { it.url == root }!!
        builder.modifyContentRootEntity(contentRoot) {
          this.excludedUrls += excludes
        }
      }
    }
  }

  override fun cleanOrphanage(builder: MutableEntityStorage) {
    entitiesToRemoveFromOrphanage.forEach {
      // This should be done before removing
      val modulePointer = it.contentRoot?.module?.createPointer<ModuleEntity>()
      val contentPointer = it.contentRoot?.createPointer<ContentRootEntity>()

      builder.removeEntity(it)

      val content = contentPointer?.resolve(builder) ?: return@forEach
      if (content.excludedUrls.isEmpty() && content.sourceRoots.isEmpty()) {
        builder.removeEntity(content)

        val module = modulePointer?.resolve(builder) ?: return@forEach
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
