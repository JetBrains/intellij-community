// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.jps.serialization

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.jps.JpsMetrics
import com.intellij.workspaceModel.ide.JpsProjectLoadedListener
import com.intellij.workspaceModel.ide.ProjectSynchronizerUtil
import com.intellij.workspaceModel.ide.impl.WorkspaceModelImpl
import io.opentelemetry.api.metrics.Meter
import org.jetbrains.annotations.ApiStatus
import kotlin.system.measureTimeMillis

/**
 * Loading the real state of the project after loading from cache.
 *
 * Initially IJ loads the state of a workspace model from the cache. In this startup activity it synchronizes the state
 * of a workspace model with project model files (iml/xml).
 *
 * If this synchronizer overrides your changes, and you'd like to postpone the changes to be after this synchronization,
 *   you can use [com.intellij.workspaceModel.ide.JpsProjectLoadingManager].
 */
@ApiStatus.Internal
class ProjectSynchronizerUtilImpl(val project: Project) : ProjectSynchronizerUtil {
  override suspend fun applyJpsModelToProjectModel() {
    val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelImpl
    if (!workspaceModel.loadedFromCache) {
      return
    }

    val projectModelSynchronizer = project.serviceAsync<JpsProjectModelSynchronizer>()
    val loadingTime = measureTimeMillis {
      val projectEntities = projectModelSynchronizer.loadProjectToEmptyStorage(project, workspaceModel)
      projectModelSynchronizer.applyLoadedStorage(projectEntities, workspaceModel)
      project.messageBus.syncPublisher(JpsProjectLoadedListener.LOADED).loaded()
    }
    ApplicationManager.getApplication().serviceAsync<ProjectSynchronizerTimeReporter>().addSyncTime(loadingTime)

    thisLogger().info(
      "Workspace model loaded from cache. Syncing real project state into workspace model in $loadingTime ms. ${Thread.currentThread()}"
    )
  }
}

@Service(Service.Level.APP)
class ProjectSynchronizerTimeReporter {
  private val syncTimeMs = MillisecondsMeasurer()

  init {
    setupOpenTelemetryReporting(JpsMetrics.getInstance().meter)
  }

  private fun setupOpenTelemetryReporting(meter: Meter) {
    val syncTimeCounter = meter.counterBuilder("workspaceModel.delayed.project.synchronizer.sync.ms").buildObserver()

    meter.batchCallback({ syncTimeCounter.record(syncTimeMs.asMilliseconds()) }, syncTimeCounter)
  }

  fun addSyncTime(time: Long) {
    syncTimeMs.duration.addAndGet(time)
  }
}