// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.LineStatusTrackerListener
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.platform.project.ProjectId
import com.intellij.platform.vcs.impl.shared.rpc.FilePathDto
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesApi
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesEvent
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesEvent.RangesUpdated
import com.intellij.util.asDisposable
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.mapNotNull

private typealias EventProducer = suspend () -> PartialChangesEvent?

internal class PartialChangesApiImpl : PartialChangesApi {
  override suspend fun partialChanges(projectId: ProjectId): Flow<PartialChangesEvent> = projectScopedCallbackFlow<EventProducer>(projectId) { project, _ ->
    val manager = LineStatusTrackerManager.getInstanceImpl(project)
    val connectionDisposable = asDisposable()

    manager.addTrackerListener(object : LineStatusTrackerManager.Listener {
      override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
        if (tracker is PartialLocalLineStatusTracker) {
          installListener(tracker, connectionDisposable)
          reportTrackerStateChanged(tracker)
        }
      }

      override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
        if (tracker is PartialLocalLineStatusTracker) {
          trySend { PartialChangesEvent.TrackerRemoved(getPathDto(tracker)) }
        }
      }
    }, connectionDisposable)

    manager.getTrackers().forEach { tracker ->
      if (tracker is PartialLocalLineStatusTracker) {
        installListener(tracker, connectionDisposable)
        reportTrackerStateChanged(tracker)
      }
    }
  }.buffer().mapNotNull { it.invoke() }

  private fun ProducerScope<EventProducer>.installListener(tracker: PartialLocalLineStatusTracker, connectionDisposable: Disposable) {
    tracker.addListener(object : PartialLocalLineStatusTracker.ListenerAdapter() {
      override fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) {
        reportTrackerStateChanged(tracker)
      }
    }, connectionDisposable)

    tracker.addListener(object : LineStatusTrackerListener {
      override fun onRangesChanged() {
        reportTrackerStateChanged(tracker)
      }
    }, connectionDisposable)
  }

  private fun ProducerScope<EventProducer>.reportTrackerStateChanged(tracker: PartialLocalLineStatusTracker) {
    trySend {
      val ranges = readAction { tracker.getRanges() }
      if (ranges != null) RangesUpdated(getPathDto(tracker), ranges) else null
    }
  }

  private fun getPathDto(tracker: PartialLocalLineStatusTracker): FilePathDto = FilePathDto.toDto(VcsUtil.getFilePath(tracker.virtualFile))
}
