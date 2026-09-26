// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class SmartModeTransitionPhase {
  EEL_DEPLOY,
  EEL_CONNECT,
  REMDEV_BACKEND_DOWNLOAD,
  REMDEV_BACKEND_VERIFYING_UNPACKING,
  REMDEV_BACKEND_INSTALLING_CONFIGURING,
  REMDEV_BACKEND_LAUNCH,
  REMDEV_BACKEND_CONNECT,
  REMDEV_BACKEND_PROJECT_LOADED,
  PLUGINS_LOADED,
  EDITORS_REOPENED,
}

@ApiStatus.Internal
interface SmartModeTransitionPhaseListener {

  fun phaseStarted(phase: SmartModeTransitionPhase) {}

  fun phaseFinished(phase: SmartModeTransitionPhase) {}

  fun phaseCompleted(phase: SmartModeTransitionPhase, startedAtMs: Long, finishedAtMs: Long) {}

  fun transitionFinished(reachedSmart: Boolean) {}

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<SmartModeTransitionPhaseListener> =
        Topic(SmartModeTransitionPhaseListener::class.java, Topic.BroadcastDirection.NONE)
  }
}