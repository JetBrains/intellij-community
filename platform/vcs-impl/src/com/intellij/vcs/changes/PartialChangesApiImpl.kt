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
import com.intellij.util.asDisposable
import com.intellij.vcs.rpc.ProjectScopeRpcHelper.projectScopedCallbackFlow
import com.intellij.vcsUtil.VcsUtil
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class PartialChangesApiImpl : PartialChangesApi {
  override suspend fun partialChanges(projectId: ProjectId): Flow<PartialChangesEvent> = projectScopedCallbackFlow(projectId) { project, _ ->
    val manager = LineStatusTrackerManager.getInstanceImpl(project)
    val connectionDisposable = asDisposable()

    manager.addTrackerListener(object : LineStatusTrackerManager.Listener {
      override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
        if (tracker is PartialLocalLineStatusTracker) {
          installListener(tracker, connectionDisposable)
          launch { sendState(tracker, channel) }
        }
      }

      override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
        if (tracker is PartialLocalLineStatusTracker) {
          launch { channel.send(PartialChangesEvent.TrackerRemoved(getPathDto(tracker))) }
        }
      }
    }, connectionDisposable)

    manager.getTrackers().forEach { tracker ->
      if (tracker is PartialLocalLineStatusTracker) {
        installListener(tracker, connectionDisposable)
        sendState(tracker, channel)
      }
    }
  }

  private fun ProducerScope<PartialChangesEvent>.installListener(tracker: PartialLocalLineStatusTracker, connectionDisposable: Disposable) {
    tracker.addListener(object : PartialLocalLineStatusTracker.ListenerAdapter() {
      override fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) {
        launch {
          sendState(tracker, channel)
        }
      }
    }, connectionDisposable)

    tracker.addListener(object : LineStatusTrackerListener {
      override fun onRangesChanged() {
        launch {
          sendState(tracker, channel)
        }
      }
    }, connectionDisposable)
  }

  private suspend fun sendState(tracker: PartialLocalLineStatusTracker, channel: SendChannel<PartialChangesEvent>) {
    val ranges = readAction { tracker.getRanges() }
    if (ranges != null) {
      channel.send(PartialChangesEvent.RangesUpdated(getPathDto(tracker), ranges))
    }
  }

  private fun getPathDto(tracker: PartialLocalLineStatusTracker): FilePathDto = FilePathDto.toDto(VcsUtil.getFilePath(tracker.virtualFile))
}