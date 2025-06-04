// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.shared.widget

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.flow.debounceBatch
import com.intellij.vcs.git.shared.repo.GitRepositoriesHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
internal class GitWidgetUpdatesNotifier(project: Project, cs: CoroutineScope) {
  private val _updates =
    MutableSharedFlow<GitWidgetUpdate>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  /**
   * "Raw" updates are debounced and deduplicated
   */
  val updates: Flow<GitWidgetUpdate> =
    channelFlow {
      launch {
        _updates.debounceBatch(100.milliseconds).collect { batchUpdate ->
          if (LOG.isTraceEnabled) {
            LOG.trace("Handling batch $batchUpdate")
          }

          val distinctEvents = batchUpdate.distinct()
          // It makes sense to merge all events into a single FULL_REFRESH if it's present in a batch
          if (GitWidgetUpdate.REFRESH in distinctEvents) {
            trySend(GitWidgetUpdate.REFRESH)
          }
          else {
            distinctEvents.forEach { trySend(it) }
          }
        }
      }
    }

  init {
    project.messageBus.connect(cs).subscribe(GitRepositoriesHolder.UPDATES, GitRepositoriesHolder.UpdatesListener { event ->
      val update = when (event) {
        GitRepositoriesHolder.UpdateType.FAVORITE_REFS_UPDATED -> GitWidgetUpdate.REPAINT
        GitRepositoriesHolder.UpdateType.REPOSITORY_CREATED -> GitWidgetUpdate.REFRESH
        GitRepositoriesHolder.UpdateType.REPOSITORY_DELETED -> GitWidgetUpdate.REFRESH
        GitRepositoriesHolder.UpdateType.REPOSITORY_STATE_UPDATED -> GitWidgetUpdate.REFRESH
        GitRepositoriesHolder.UpdateType.TAGS_LOADED -> GitWidgetUpdate.REFRESH_TAGS
        GitRepositoriesHolder.UpdateType.TAGS_HIDDEN -> GitWidgetUpdate.REFRESH
      }

      if (LOG.isDebugEnabled) {
        LOG.debug("Transformed $event to $update")
      }

      _updates.tryEmit(update)
    })
  }

  internal fun refresh() {
    _updates.tryEmit(GitWidgetUpdate.REFRESH)
  }

  companion object {
    private val LOG = Logger.getInstance(GitWidgetUpdatesNotifier::class.java)

    fun getInstance(project: Project): GitWidgetUpdatesNotifier = project.getService(GitWidgetUpdatesNotifier::class.java)
  }
}

@ApiStatus.Internal
enum class GitWidgetUpdate {
  /**
   * Reloads or hides tags node preserving the filtering state
   */
  REFRESH_TAGS,

  /**
   * Just repaints nodes without affecting nodes' order or hierarchy
   */
  REPAINT,

  /**
   * Refresh model and repaints tree preserving the filtering state
   */
  REFRESH
}