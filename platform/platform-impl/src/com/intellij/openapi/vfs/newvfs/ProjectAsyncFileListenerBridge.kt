// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.ProjectBulkFileListener
import com.intellij.openapi.vfs.impl.ProjectBulkFileListenerRegistryImpl
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.concurrency.annotations.RequiresReadLock

/**
 * Construct project-specific event lists and invokes [com.intellij.openapi.vfs.ProjectBulkFileListener] with them
 */
internal class ProjectAsyncFileListenerBridge : AsyncFileListener {
  override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
    val batches = snapshotActiveProjectBatches(events)
    if (batches.isEmpty()) return null
    return BatchChangeApplier(batches)
  }

  @RequiresReadLock
  private fun snapshotActiveProjectBatches(events: List<VFileEvent>): List<ProjectBatch> {
    val projectManager = ProjectManager.getInstanceIfCreated() ?: return emptyList()
    return projectManager.openProjects.mapNotNull { project ->
      val registry = ProjectBulkFileListenerRegistryImpl.getInstanceImpl(project) ?: return@mapNotNull null
      val listeners = registry.snapshotListeners()
      if (listeners.isEmpty()) null else ProjectBatch(project, listeners, ProjectVfsEventBatch(project, events))
    }
  }

  private data class ProjectBatch(
    val project: Project,
    val listeners: List<ProjectBulkFileListener>,
    val batch: ProjectVfsEventBatch,
  )

  private class BatchChangeApplier(
    private val batches: List<ProjectBatch>,
  ) : AsyncFileListener.ChangeApplier {
    override fun afterVfsChange() {
      for ((project, listeners, batch) in batches) {
        val afterEvents = batch.getAfterEvents()
        for (listener in listeners) {
          runHandlingExceptions { listener.afterVfsChange(afterEvents, project) }
        }
      }
    }
  }
}

private val LOG = Logger.getInstance(ProjectAsyncFileListenerBridge::class.java)

private inline fun runHandlingExceptions(block: () -> Unit): Unit = try {
  block()
}
catch (e: Throwable) {
  rethrowControlFlowException(e)
  LOG.error(e)
}
