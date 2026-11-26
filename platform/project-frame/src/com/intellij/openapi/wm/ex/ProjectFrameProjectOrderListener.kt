// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.ex

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Hears that the order of the projects of a frame changed, see [ProjectFrameProjectOrderProvider.getProjectOrder].
 *
 * A UI that lists the projects of a frame reads the order when a project joins or leaves the frame. An order that
 * changes with the same projects gives it no such event. The owner of the order then publishes this topic on the
 * message bus of the frame owner, on the EDT, and the UI reads the order again.
 */
@Internal
@Experimental
fun interface ProjectFrameProjectOrderListener {
  fun projectOrderChanged()

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<ProjectFrameProjectOrderListener> =
      Topic(ProjectFrameProjectOrderListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
