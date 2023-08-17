// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.util.messages.Topic
import java.util.*

/**
 * Discussion about this listener: IDEA-330045
 */
interface JpsProjectLoadedListener : EventListener {
  /** This method will be executed right after JPS project model loading */
  fun loaded()

  companion object {
    @Topic.ProjectLevel
    val LOADED: Topic<JpsProjectLoadedListener> = Topic(JpsProjectLoadedListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
