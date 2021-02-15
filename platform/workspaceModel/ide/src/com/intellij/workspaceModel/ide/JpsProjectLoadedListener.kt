// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.ide

import com.intellij.util.messages.Topic
import java.util.*

interface JpsProjectLoadedListener : EventListener {
  /** This method will be executed right after JPS project model loading */
  fun loaded()

  companion object {
    @Topic.ProjectLevel
    val LOADED = Topic(JpsProjectLoadedListener::class.java, Topic.BroadcastDirection.NONE)
  }
}
