// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import java.util.*

abstract class GHPRTimelineMergedEvents<T : GHPRTimelineEvent>
  : GHPRTimelineEvent {

  private val events = mutableListOf<T>()

  override val actor: GHActor?
    get() = events.last().actor
  override val createdAt: Date
    get() = events.last().createdAt

  fun add(event: T) {
    events.add(event)
    if (event is GHPRTimelineMergedEvents<*>) {
      for (evt in event.events) {
        @Suppress("UNCHECKED_CAST")
        add(evt as T)
      }
    }
    else {
      addNonMergedEvent(event)
    }
  }

  protected abstract fun addNonMergedEvent(event: T)

  abstract fun hasAnyChanges(): Boolean
}