// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

/**
 * Listens to the changes of the back places and the change places in the [IdeDocumentHistory].
 * See [PlaceInfo] for both lists.
 *
 * Subscribe to [TOPIC] on the project message bus, or register the listener in `plugin.xml` under `projectListeners`.
 * The history sends the events on the EDT, so keep the handlers fast.
 *
 * One action of the user can send several events. For example, a new place replaces the last place when both
 * have the same position. Then the listener gets a removal of the old place and an addition of the new place.
 * The history also removes the oldest place when a list becomes too long.
 *
 * The history does not report all removals. It sends no event when it clears the history or drops the places of
 * deleted files. Do not use the events to keep an exact copy of a list.
 */
@ApiStatus.Experimental
interface RecentPlacesListener {
  companion object {
    @Topic.ProjectLevel
    @JvmField
    val TOPIC: Topic<RecentPlacesListener> = Topic(RecentPlacesListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /**
   * The history added [place] at the end of a list.
   *
   * @param isChanged `true` if the history added the place to the change places, `false` if to the back places
   * @param groupId the group ID of the command that caused the addition, or `null` if the command has no group
   */
  fun recentPlaceAdded(place: PlaceInfo, isChanged: Boolean, groupId: Any?) {
  }

  /**
   * The history removed [place] from a list.
   *
   * @param isChanged `true` if the history removed the place from the change places, `false` if from the back places
   */
  fun recentPlaceRemoved(place: PlaceInfo, isChanged: Boolean) {
  }
}
