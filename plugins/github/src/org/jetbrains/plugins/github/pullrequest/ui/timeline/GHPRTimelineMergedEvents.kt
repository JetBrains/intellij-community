// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.GHActor
import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineEvent
import java.util.*

abstract class GHPRTimelineMergedEvents<T : GHPRTimelineEvent> : GHPRTimelineGroupedItems<T>(), GHPRTimelineEvent {
  override val actor: GHActor?
    get() = items.last().actor
  override val createdAt: Date
    get() = items.last().createdAt

  override fun add(item: T) {
    super.add(item)
    if (item is GHPRTimelineMergedEvents<*>) {
      for (evt in item.items) {
        @Suppress("UNCHECKED_CAST")
        add(evt as T)
      }
    }
    else {
      addNonMergedEvent(item)
    }
  }

  protected abstract fun addNonMergedEvent(event: T)

  abstract fun hasAnyChanges(): Boolean
}