// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic

import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
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
  REMDEV_BACKEND_PLUGINS_PROVISIONED,
  REMDEV_BACKEND_PROJECT_LOADED,
  PLUGINS_LOADED,
  EDITORS_REOPENED,
}

/** The fields a phase finish can carry, shared by the publisher of the phase and the collector. */
@ApiStatus.Internal
object SmartModeTransitionFields {
  /**
   * On the finish of [SmartModeTransitionPhase.REMDEV_BACKEND_PLUGINS_PROVISIONED]: whether the host restarted to
   * load the plugins it provisioned.
   */
  @JvmField
  val HOST_RESTARTED: BooleanEventField = EventFields.Boolean("host_restarted")
}

@ApiStatus.Internal
interface SmartModeTransitionPhaseListener {

  fun phaseStarted(phase: SmartModeTransitionPhase) {}

  fun phaseFinished(phase: SmartModeTransitionPhase) {}

  /**
   * A finish with [fields] on its event. The collector registers the fields a phase can carry, see
   * [SmartModeTransitionFields]. Defaults to the plain [phaseFinished], so a listener that reads no field
   * overrides only that one.
   */
  fun phaseFinished(phase: SmartModeTransitionPhase, fields: List<EventPair<*>>) {
    phaseFinished(phase)
  }

  fun phaseCompleted(phase: SmartModeTransitionPhase, startedAtMs: Long, finishedAtMs: Long) {}

  fun transitionFinished(reachedSmart: Boolean) {}

  companion object {
    @JvmField
    @Topic.AppLevel
    val TOPIC: Topic<SmartModeTransitionPhaseListener> =
        Topic(SmartModeTransitionPhaseListener::class.java, Topic.BroadcastDirection.NONE)
  }
}