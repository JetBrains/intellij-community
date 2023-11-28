// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import org.jetbrains.plugins.github.api.data.pullrequest.timeline.GHPRTimelineItem

abstract class GHPRTimelineGroupedItems<T : GHPRTimelineItem> : GHPRTimelineItem {
  val items = mutableListOf<T>()

  open fun add(item: T) {
    items.add(item)
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GHPRTimelineGroupedItems<*>) return false

    if (items != other.items) return false

    return true
  }

  override fun hashCode(): Int {
    return items.hashCode()
  }
}