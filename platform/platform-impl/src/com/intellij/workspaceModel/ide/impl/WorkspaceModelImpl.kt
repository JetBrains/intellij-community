// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.FacetEntity
import com.intellij.workspaceModel.storage.bridgeEntities.FacetId
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleId
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import kotlin.system.measureTimeMillis

class WorkspaceModelImpl(private val project: Project) : WorkspaceModel, Disposable {
  private val cacheEnabled = (!ApplicationManager.getApplication().isUnitTestMode && WorkspaceModelImpl.cacheEnabled) || forceEnableCaching
  override val cache = if (cacheEnabled) WorkspaceModelCacheImpl(project, this) else null

  /** specifies ID of a entity which changes should be printed to the log */
  private val entityToTrace = System.getProperty("idea.workspace.model.track.facet.id")?.let {
    val (moduleName, facetTypeId, facetName) = it.split('/')
    FacetId(facetName, facetTypeId, ModuleId(moduleName))
  }

  @Volatile
  var loadedFromCache = false
    private set

  override val entityStorage: VersionedEntityStorageImpl

  init {
    // TODO It's possible to load this cache from the moment we know project path
    //  Like in ProjectLifecycleListener or something

    log.debug { "Loading workspace model" }

    val initialContent = WorkspaceModelInitialTestContent.pop()
    val projectEntities = when {
      initialContent != null -> initialContent
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("(wm) Loading cache")
        val previousStorage: WorkspaceEntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()
        }
        val storage = if (previousStorage != null) {
          log.info("Load workspace model from cache in $loadingCacheTime ms")
          loadedFromCache = true
          printInfoAboutTracedEntity(previousStorage, "cache")
          previousStorage
        }
        else WorkspaceEntityStorageBuilder.create()
        activity.end()
        storage
      }
      else -> WorkspaceEntityStorageBuilder.create()
    }

    entityStorage = VersionedEntityStorageImpl((projectEntities as? WorkspaceEntityStorageBuilder)?.toStorage() ?: projectEntities)
    if (entityToTrace != null) {
      WorkspaceModelTopics.getInstance(project).subscribeImmediately(project.messageBus.connect(), EntityTracingListener(entityToTrace))
    }
  }

  fun blockDelayedLoading() {
    loadedFromCache = false
  }

  internal fun printInfoAboutTracedEntity(storage: WorkspaceEntityStorage, storageDescription: String) {
    if (entityToTrace != null) {
      log.info("Traced entity from $storageDescription: ${storage.resolve(entityToTrace)?.configurationXmlTag}")
    }
  }

  override fun <R> updateProjectModel(updater: (WorkspaceEntityStorageBuilder) -> R): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val before = entityStorage.current
    val builder = WorkspaceEntityStorageBuilder.from(before)
    val result = updater(builder)
    startPreUpdateHandlers(before, builder)
    val changes = builder.collectChanges(before)
    entityStorage.replace(builder.toStorage(), changes, this::onBeforeChanged, this::onChanged)
    return result
  }

  override fun <R> updateProjectModelSilent(updater: (WorkspaceEntityStorageBuilder) -> R): R {
    val builder = WorkspaceEntityStorageBuilder.from(entityStorage.current)
    val result = updater(builder)
    entityStorage.replaceSilently(builder.toStorage())
    return result
  }

  override fun getBuilderSnapshot(): BuilderSnapshot {
    val current = entityStorage.pointer
    return BuilderSnapshot(current.version, current.storage)
  }

  override fun replaceProjectModel(replacement: StorageReplacement): Boolean {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    if (entityStorage.version != replacement.version) return false

    entityStorage.replace(replacement.snapshot, replacement.changes, this::onBeforeChanged, this::onChanged)

    return true
  }

  override fun dispose() = Unit

  private fun onBeforeChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    WorkspaceModelTopics.getInstance(project).syncPublisher(project.messageBus).beforeChanged(change)
  }

  private fun onChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    WorkspaceModelTopics.getInstance(project).syncPublisher(project.messageBus).changed(change)
  }

  private fun startPreUpdateHandlers(before: WorkspaceEntityStorage, builder: WorkspaceEntityStorageBuilder) {
    var startUpdateLoop = true
    var updatesStarted = 0
    while (startUpdateLoop && updatesStarted < PRE_UPDATE_LOOP_BLOCK) {
      updatesStarted += 1
      startUpdateLoop = false
      PRE_UPDATE_HANDLERS.extensions().forEach {
        startUpdateLoop = startUpdateLoop or it.update(before, builder)
      }
    }
    if (updatesStarted >= PRE_UPDATE_LOOP_BLOCK) {
      log.error("Loop workspace model updating")
    }
  }

  companion object {
    private val log = logger<WorkspaceModelImpl>()
    const val ENABLED_CACHE_KEY = "ide.new.project.model.cache"

    var forceEnableCaching = false
    val cacheEnabled = Registry.`is`(ENABLED_CACHE_KEY)

    private val PRE_UPDATE_HANDLERS = ExtensionPointName.create<WorkspaceModelPreUpdateHandler>("com.intellij.workspaceModel.preUpdateHandler")
    private const val PRE_UPDATE_LOOP_BLOCK = 100
  }
}

private class EntityTracingListener(private val entityId: FacetId) : WorkspaceModelChangeListener {
  override fun changed(event: VersionedStorageChange) {
    event.getAllChanges().forEach {
      when (it) {
        is EntityChange.Added -> printInfo("added", it.entity)
        is EntityChange.Removed -> printInfo("removed", it.entity)
        is EntityChange.Replaced -> {
          printInfo("replaced from", it.oldEntity)
          printInfo("replaced to", it.newEntity)
        }
      }
    }
  }

  private fun printInfo(action: String, entity: WorkspaceEntity) {
    if ((entity as? FacetEntity)?.persistentId() == entityId) {
      LOG.info("$action: ${entity.configurationXmlTag}", Throwable())
    }
  }

  companion object {
    private val LOG = logger<EntityTracingListener>()
  }
}
