// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class SmartModeTransitionPhase {
  EEL_DEPLOY,
  EEL_CONNECT,
  REMDEV_BACKEND_DEPLOY,
  REMDEV_BACKEND_CONNECT,
  REMDEV_BACKEND_PROJECT_LOADED,
  PLUGINS_LOADED,
  EDITORS_REOPENED,
}

@ApiStatus.Internal
interface SmartModeTransitionPhaseListener {

  fun phaseStarted(phase: SmartModeTransitionPhase) {}

  fun phaseFinished(phase: SmartModeTransitionPhase) {}

  fun transitionFinished(reachedSmart: Boolean) {}

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<SmartModeTransitionPhaseListener> =
        Topic(SmartModeTransitionPhaseListener::class.java, Topic.BroadcastDirection.NONE)
  }
}