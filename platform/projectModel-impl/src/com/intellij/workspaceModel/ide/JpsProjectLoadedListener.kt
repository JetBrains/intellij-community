// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.util.messages.Topic
import java.util.*

interface JpsProjectLoadedListener : EventListener {
  /** This method will be executed right after JPS project model loading */
  fun loaded()

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val LOADED = Topic(JpsProjectLoadedListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
