// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer.startActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.ide.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import com.intellij.workspaceModel.storage.impl.assertConsistency
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

open class WorkspaceModelImpl(private val project: Project) : WorkspaceModel, Disposable {
  @Volatile
  var loadedFromCache = false
    private set

  final override val entityStorage: VersionedEntityStorageImpl

  val entityTracer: EntityTracingLogger = EntityTracingLogger()

  var userWarningLoggingLevel = false
    @TestOnly set

  private var projectModelUpdating = AtomicBoolean(false)

  init {
    log.debug { "Loading workspace model" }

    val initialContent = WorkspaceModelInitialTestContent.pop()
    val cache = WorkspaceModelCache.getInstance(project)
    val projectEntities: MutableEntityStorage = when {
      initialContent != null -> {
        loadedFromCache = initialContent !== EntityStorageSnapshot.empty()
        initialContent.toBuilder()
      }
      cache != null -> {
        val activity = startActivity("cache loading")
        val previousStorage: MutableEntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()?.toBuilder()
        }
        val storage = if (previousStorage == null) {
          MutableEntityStorage.create()
        }
        else {
          log.info("Load workspace model from cache in $loadingCacheTime ms")
          loadedFromCache = true
          entityTracer.printInfoAboutTracedEntity(previousStorage, "cache")
          previousStorage
        }
        activity.end()
        storage
      }
      else -> MutableEntityStorage.create()
    }

    @Suppress("LeakingThis")
    prepareModel(project, projectEntities)

    entityStorage = VersionedEntityStorageImpl(projectEntities.toSnapshot())
    entityTracer.subscribe(project)
  }

  /**
   * Used only in Rider IDE
   */
  @ApiStatus.Internal
  open fun prepareModel(project: Project, storage: MutableEntityStorage) = Unit

  fun ignoreCache() {
    loadedFromCache = false
  }

  final override fun <R> updateProjectModel(updater: (MutableEntityStorage) -> R): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    if (!projectModelUpdating.compareAndSet(false, true)) {
      throw RuntimeException("Recursive call to `updateProjectModel` is not allowed")
    }
    val before = entityStorage.current
    val builder = MutableEntityStorage.from(before)
    val result = updater(builder)
    startPreUpdateHandlers(before, builder)
    val changes = builder.collectChanges(before)
    val newStorage = builder.toSnapshot()
    if (Registry.`is`("ide.workspace.model.assertions.on.update", false)) {
      before.assertConsistency()
      newStorage.assertConsistency()
    }
    entityStorage.replace(newStorage, changes, this::onBeforeChanged, this::onChanged, projectModelUpdating)
    return result
  }

  final override fun <R> updateProjectModelSilent(updater: (MutableEntityStorage) -> R): R {
    if (!projectModelUpdating.compareAndSet(false, true)) {
      //throw RuntimeException("Recursive call to `updateProjectModel` is not allowed")
      // Need to fix all cases and change to the runtime exception
      log.warn("Recursive call to `updateProjectModel` is not allowed")
    }
    val before = entityStorage.current
    val builder = MutableEntityStorage.from(entityStorage.current)
    val result = updater(builder)
    val newStorage = builder.toSnapshot()
    if (Registry.`is`("ide.workspace.model.assertions.on.update", false)) {
      before.assertConsistency()
      newStorage.assertConsistency()
    }
    entityStorage.replaceSilently(newStorage)
    projectModelUpdating.set(false)
    return result
  }

  final override fun getBuilderSnapshot(): BuilderSnapshot {
    val current = entityStorage.pointer
    return BuilderSnapshot(current.version, current.storage)
  }

  final override fun replaceProjectModel(replacement: StorageReplacement): Boolean {
    ApplicationManager.getApplication().assertWriteAccessAllowed()

    if (entityStorage.version != replacement.version) return false

    entityStorage.replace(replacement.snapshot, replacement.changes, this::onBeforeChanged, this::onChanged, null)

    return true
  }

  final override fun dispose() = Unit

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
      val message = "Exception at Workspace Model event handling"
      if (userWarningLoggingLevel) {
        log.warn(message, e)
      } else {
        log.error(message, e)
      }
    }
  }

  companion object {
    private val log = logger<WorkspaceModelImpl>()

    private val PRE_UPDATE_HANDLERS = ExtensionPointName.create<WorkspaceModelPreUpdateHandler>("com.intellij.workspaceModel.preUpdateHandler")
    private const val PRE_UPDATE_LOOP_BLOCK = 100
  }
}
