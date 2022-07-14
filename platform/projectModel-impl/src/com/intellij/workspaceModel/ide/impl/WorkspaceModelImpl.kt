// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import kotlin.system.measureTimeMillis

class WorkspaceModelImpl(private val project: Project) : WorkspaceModel, Disposable {
  override val cache: WorkspaceModelCache?
    get() = WorkspaceModelCache.getInstance(project)

  @Volatile
  var loadedFromCache = false
    private set

  override val entityStorage: VersionedEntityStorageImpl

  val entityTracer: EntityTracingLogger = EntityTracingLogger()

  init {
    // TODO It's possible to load this cache from the moment we know project path
    //  Like in ProjectLifecycleListener or something

    log.debug { "Loading workspace model" }

    val initialContent = WorkspaceModelInitialTestContent.pop()
    val cache = WorkspaceModelCache.getInstance(project)
    val projectEntities: EntityStorageSnapshot = when {
      initialContent != null -> initialContent
      cache != null -> {
        val activity = startActivity("cache loading", ActivityCategory.DEFAULT)
        val previousStorage: EntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()
        }
        val storage = if (previousStorage != null) {
          log.info("Load workspace model from cache in $loadingCacheTime ms")
          loadedFromCache = true
          entityTracer.printInfoAboutTracedEntity(previousStorage, "cache")
          previousStorage.toSnapshot()
        }
        else MutableEntityStorage.create().toSnapshot()
        activity.end()
        storage
      }
      else -> MutableEntityStorage.create().toSnapshot()
    }

    entityStorage = VersionedEntityStorageImpl(projectEntities)
    entityTracer.subscribe(project)
  }

  fun ignoreCache() {
    loadedFromCache = false
  }

  override fun <R> updateProjectModel(updater: (MutableEntityStorage) -> R): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val before = entityStorage.current
    val builder = MutableEntityStorage.from(before)
    val result = updater(builder)
    startPreUpdateHandlers(before, builder)
    val changes = builder.collectChanges(before)
    entityStorage.replace(builder.toSnapshot(), changes, this::onBeforeChanged, this::onChanged)
    return result
  }

  override fun <R> updateProjectModelSilent(updater: (MutableEntityStorage) -> R): R {
    val builder = MutableEntityStorage.from(entityStorage.current)
    val result = updater(builder)
    entityStorage.replaceSilently(builder.toSnapshot())
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
    /**
     * Order of events: initialize project libraries, initialize module bridge + module friends, all other listeners
     */

    val workspaceModelTopics = WorkspaceModelTopics.getInstance(project)
    logErrorOnEventHandling {
      workspaceModelTopics.syncProjectLibs(project.messageBus).beforeChanged(change)
    }
    logErrorOnEventHandling {
      workspaceModelTopics.syncModuleBridge(project.messageBus).beforeChanged(change)
    }
    logErrorOnEventHandling {
      workspaceModelTopics.syncPublisher(project.messageBus).beforeChanged(change)
    }
  }

  private fun onChanged(change: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (project.isDisposed) return
    val workspaceModelTopics = WorkspaceModelTopics.getInstance(project)
    logErrorOnEventHandling {
      workspaceModelTopics.syncProjectLibs(project.messageBus).changed(change)
    }
    logErrorOnEventHandling {
      workspaceModelTopics.syncModuleBridge(project.messageBus).changed(change)
    }
    logErrorOnEventHandling {
      workspaceModelTopics.syncPublisher(project.messageBus).changed(change)
    }
  }

  private fun startPreUpdateHandlers(before: EntityStorage, builder: MutableEntityStorage) {
    var startUpdateLoop = true
    var updatesStarted = 0
    while (startUpdateLoop && updatesStarted < PRE_UPDATE_LOOP_BLOCK) {
      updatesStarted += 1
      startUpdateLoop = false
      PRE_UPDATE_HANDLERS.extensionsIfPointIsRegistered.forEach {
        startUpdateLoop = startUpdateLoop or it.update(before, builder)
      }
    }
    if (updatesStarted >= PRE_UPDATE_LOOP_BLOCK) {
      log.error("Loop workspace model updating")
    }
  }

  private fun logErrorOnEventHandling(action: () -> Unit) {
    try {
      action.invoke()
    } catch (e: Throwable) {
      log.warn("Exception at Workspace Model event handling", e)
    }
  }

  companion object {
    private val log = logger<WorkspaceModelImpl>()

    private val PRE_UPDATE_HANDLERS = ExtensionPointName.create<WorkspaceModelPreUpdateHandler>("com.intellij.workspaceModel.preUpdateHandler")
    private const val PRE_UPDATE_LOOP_BLOCK = 100
  }
}
