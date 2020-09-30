// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.WorkspaceModelTopics
import com.intellij.workspaceModel.storage.VersionedStorageChange
import com.intellij.workspaceModel.storage.WorkspaceEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntityStorageBuilder
import com.intellij.workspaceModel.storage.impl.VersionedEntityStorageImpl
import kotlin.system.measureTimeMillis

class WorkspaceModelImpl(private val project: Project) : WorkspaceModel, Disposable {

  private val projectEntities: WorkspaceEntityStorageBuilder

  private val cacheEnabled = (!ApplicationManager.getApplication().isUnitTestMode && WorkspaceModelImpl.cacheEnabled) || forceEnableCaching
  private val cache = if (cacheEnabled) WorkspaceModelCacheImpl(project, this) else null

  @Volatile
  var loadedFromCache = false
    private set

  override val entityStorage: VersionedEntityStorageImpl

  init {
    // TODO It's possible to load this cache from the moment we know project path
    //  Like in ProjectLifecycleListener or something

    log.debug { "Loading workspace model" }
    val initialContent = WorkspaceModelInitialTestContent.pop()
    when {
      initialContent != null -> projectEntities = WorkspaceEntityStorageBuilder.from(initialContent)
      cache != null -> {
        val activity = StartUpMeasurer.startActivity("(wm) Loading cache")
        val previousStorage: WorkspaceEntityStorage?
        val loadingCacheTime = measureTimeMillis {
          previousStorage = cache.loadCache()
        }
        projectEntities = if (previousStorage != null) {
          log.info("Load workspace model from cache in $loadingCacheTime ms")
          loadedFromCache = true
          WorkspaceEntityStorageBuilder.from(previousStorage)
        }
        else WorkspaceEntityStorageBuilder.create()
        activity.end()
      }
      else -> projectEntities = WorkspaceEntityStorageBuilder.create()
    }

    entityStorage = VersionedEntityStorageImpl(projectEntities.toStorage())
  }

  override fun <R> updateProjectModel(updater: (WorkspaceEntityStorageBuilder) -> R): R {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    val before = projectEntities.toStorage()
    val result = updater(projectEntities)
    val changes = projectEntities.collectChanges(before)
    projectEntities.resetChanges()
    entityStorage.replace(projectEntities.toStorage(), changes, this::onBeforeChanged, this::onChanged)
    return result
  }

  override fun <R> updateProjectModelSilent(updater: (WorkspaceEntityStorageBuilder) -> R): R {
    val result = updater(projectEntities)
    entityStorage.replaceSilently(projectEntities.toStorage())
    return result
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

  companion object {
    private val log = logger<WorkspaceModelImpl>()
    const val ENABLED_CACHE_KEY = "ide.new.project.model.cache"

    var forceEnableCaching = false
    val cacheEnabled = Registry.`is`(ENABLED_CACHE_KEY)
  }
}
