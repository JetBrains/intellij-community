// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.system.measureTimeMillis

private val LOG = logger<InitialVfsRefreshService>()

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class InitialVfsRefreshService(private val project: Project, coroutineScope: CoroutineScope) {
  private val job = coroutineScope.launch(start = CoroutineStart.LAZY) { refresh() }

  fun scheduleInitialVfsRefresh() {
    job.start()
  }

  fun runInitialVfsRefresh() {
    runBlockingCancellable {
      awaitInitialVfsRefreshFinished()
    }
  }

  fun isInitialVfsRefreshFinished(): Boolean = job.isCompleted

  suspend fun awaitInitialVfsRefreshFinished() {
    job.join()
  }

  private suspend fun refresh() {
    val projectId = project.getLocationHash()
    if (System.getProperty("ij.indexes.skip.initial.refresh").toBoolean() || application.isUnitTestMode()) {
      LOG.debug { "$projectId: initial VFS refresh skipped" }
      return
    }

    LOG.info("$projectId: marking roots for initial VFS refresh")
    val roots = ProjectRootManagerEx.getInstanceEx(project).markRootsForRefresh()
    LOG.info("$projectId: starting initial VFS refresh of ${roots.size} roots")

    val durationMs = measureTimeMillis { RefreshQueue.getInstance().refresh(true, roots) }

    LOG.info("$projectId: initial VFS refresh finished in $durationMs ms")
    VfsUsageCollector.logInitialRefresh(project, durationMs)
  }
}
