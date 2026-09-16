// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.ComputableSequence
import com.intellij.collaboration.util.ListPart
import com.intellij.collaboration.util.SequenceComputer
import kotlinx.coroutines.flow.Flow
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem

interface GHPRTimelineDataProvider {

  /**
   * Signals that the whole [getTimelineItems] sequence might have changed
   */
  val timelineChangeSignal: Flow<Unit>

  /**
   * Signals that the new items might have appeared in the [getTimelineItems] sequence,
   * and a client might want to call [com.intellij.collaboration.util.SequenceComputer.computeNext] again
   */
  val timelineUpdateSignal: Flow<Unit>

  /**
   * Returns an "infinite" [SequenceComputer] which can be polled for new items
   */
  fun getTimelineItems(): SequenceComputer<ListPart<GHPRTimelineItem>>

  /**
   * Clear the caches and signal that the timeline needs to be fully reloaded
   */
  fun signalTimelineNeedsReload()
}