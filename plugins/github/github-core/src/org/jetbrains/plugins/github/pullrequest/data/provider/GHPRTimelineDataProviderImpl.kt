// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.ListPartSequenceWithMutableCache
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jetbrains.plugins.github.api.data.GHIssueComment
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestReview
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRTimelineService

internal class GHPRTimelineDataProviderImpl(
  parentCs: CoroutineScope,
  timelineService: GHPRTimelineService,
  messageBus: MessageBus,
  id: GHPRIdentifier,
) : GHPRTimelineDataProvider {
  private val cs = parentCs.childScope(javaClass.name)

  private val _changeSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val timelineChangeSignal: Flow<Unit> = _changeSignal.asSharedFlow()

  private val _updateSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  override val timelineUpdateSignal: Flow<Unit> = _updateSignal.asSharedFlow()

  private val loader = ListPartSequenceWithMutableCache(cs, timelineService.getTimelineItems(id))

  init {
    // The granular in-place item updates the old loader performed are preserved on the cache below, but instead of
    // firing item-level notifications we just tell consumers the whole sequence might have changed.
    messageBus.connect(cs).subscribe(GHPRDataOperationsListener.TOPIC, object : GHPRDataOperationsListener {
      override fun onMetadataChanged() = signalUpdate()

      override fun onCommentAdded() = signalUpdate()

      override fun onCommentUpdated(commentId: String, newBody: String) {
        cs.launch {
          loader.updateItem { it.asSafely<GHIssueComment>()?.takeIf { c -> c.id == commentId }?.copy(body = newBody) }
          _changeSignal.emit(Unit)
        }
      }

      override fun onCommentDeleted(commentId: String) {
        // need to reload the whole thing, bc the cursors might have changed
        signalTimelineNeedsReload()
      }

      override fun onReviewsChanged() = signalUpdate()

      override fun onReviewUpdated(reviewId: String, newBody: String) {
        cs.launch {
          loader.updateItem { it.asSafely<GHPullRequestReview>()?.takeIf { r -> r.id == reviewId }?.copy(body = newBody) }
          _changeSignal.emit(Unit)
        }
      }
    })
  }

  private fun signalUpdate() {
    _updateSignal.tryEmit(Unit)
  }


  override fun getTimelineItems(): SequenceComputer<ListPart<GHPRTimelineItem>> {
    return loader.getComputer()
  }

  override fun signalTimelineNeedsReload() {
    loader.reset()
    _changeSignal.tryEmit(Unit)
  }
}