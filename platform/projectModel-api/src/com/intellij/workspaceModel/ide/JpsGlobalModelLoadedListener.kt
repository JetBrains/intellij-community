// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
interface JpsGlobalModelLoadedListener : EventListener {
  /** This method will be executed right after JPS global model loading e.g., SDK or global libraries */
  fun loaded()

  companion object {
    @Topic.AppLevel
    val LOADED: Topic<JpsGlobalModelLoadedListener> = Topic(JpsGlobalModelLoadedListener::class.java, Topic.BroadcastDirection.NONE, true)
  }
}
