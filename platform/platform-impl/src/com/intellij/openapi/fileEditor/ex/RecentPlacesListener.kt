// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface RecentPlacesListener {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<RecentPlacesListener> = Topic(RecentPlacesListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun recentPlaceAdded(changePlace: PlaceInfo, isChanged: Boolean, groupId: Any?)

  fun recentPlaceRemoved(changePlace: PlaceInfo, isChanged: Boolean)
}
