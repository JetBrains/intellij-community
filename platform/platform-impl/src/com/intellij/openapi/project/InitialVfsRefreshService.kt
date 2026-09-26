// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.vfs.newvfs.RefreshQueue
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.util.AwaitCancellationAndInvoke
import com.intellij.util.application
import com.intellij.util.awaitCancellationAndInvoke
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * _Initiate_ VFS in-memory state syncing with on-disk FS state:
 * - marks all the VFS roots as 'dirty' (which effectively marks all VFS files 'dirty')
 * - initiate _asynchronous_ refresh, **without waiting for it to finish**
 *
 * Historically, this is needed for **quite a specific case**: some files belonging to project B were changed while only
 * project A is opened, and project B is opened afterward. In this case `fsnotifier` doesn't watch for changes in B's files
 * while project B is not opened => VFS events for changes are not generated => on B opening, there is no way to know some
 * VFS files are out of sync with the disk. [InitialVfsRefreshService] fixes that by forcing entire VFS in-memory state to
 * be synced -- rough, overkill, but works.
 *
 * It turns out to be more generally useful, though: by some reasons, we don't have a rule to do `Virtualfile.isDirty() -> refresh()`
 * before any `VirtualFile` access -- which makes code vulnerable in cases some FS changes are not yet synced. This service
 * together with a couple others (like force sync on IDE focus lost-gain) +/- fixes that issue, by closing most of the
 * un-synced windows -- but not all of them.
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class InitialVfsRefreshService(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val started: AtomicBoolean = AtomicBoolean(false)
  private val job = CompletableDeferred<Unit>(parent = coroutineScope.coroutineContext.job)

  @Suppress("DuplicatedCode")
  fun scheduleInitialVfsRefresh() {
    if (started.getAndSet(true)) {
      return
    }

    val projectId = project.getLocationHash()
    val logger = logger<InitialVfsRefreshService>()
    if (System.getProperty("ij.indexes.skip.initial.refresh").toBoolean() || application.isUnitTestMode()) {
      logger.debug { "$projectId: initial VFS refresh skipped" }
      job.complete(Unit)
      return
    }

    coroutineScope.launch {
      @OptIn(AwaitCancellationAndInvoke::class)
      try {
        logger.info("$projectId: marking roots for initial VFS refresh")
        val roots = ProjectRootManagerEx.getInstanceEx(project).markRootsForRefresh()
        logger.info("$projectId: starting initial VFS refresh of ${roots.size} roots")
        val t = System.nanoTime()
        RefreshQueue.getInstance().refresh(/*async: */true, roots)
        val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
        logger.info("${projectId}: initial VFS refresh finished in ${duration} ms")
        VfsUsageCollector.logInitialRefresh(project, duration)
      }
      finally {
        job.complete(Unit)
      }
    }
  }

  @Suppress("DuplicatedCode")
  fun runInitialVfsRefresh() {
    if (started.getAndSet(true)) {
      return
    }

    val projectId = project.getLocationHash()
    val logger = logger<InitialVfsRefreshService>()
    if (System.getProperty("ij.indexes.skip.initial.refresh").toBoolean() || application.isUnitTestMode()) {
      logger.debug { "${projectId}: initial VFS refresh skipped" }
      job.complete(Unit)
      return
    }

    @OptIn(AwaitCancellationAndInvoke::class)
    try {
      logger.info("$projectId: marking roots for initial VFS refresh")
      val roots = ProjectRootManagerEx.getInstanceEx(project).markRootsForRefresh()
      logger.info("$projectId: starting initial VFS refresh of ${roots.size} roots")
      val session = RefreshQueue.getInstance().createSession(false, true, null)
      coroutineScope.awaitCancellationAndInvoke { session.cancel() }
      session.addAllFiles(roots)
      val t = System.nanoTime()
      session.launch()
      val duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t)
      logger.info("$projectId: initial VFS refresh finished in $duration ms")
      VfsUsageCollector.logInitialRefresh(project, duration)
    }
    finally {
      job.complete(Unit)
    }
  }

  fun isInitialVfsRefreshFinished(): Boolean = job.isCompleted

  suspend fun awaitInitialVfsRefreshFinished(): Unit = job.await()
}
