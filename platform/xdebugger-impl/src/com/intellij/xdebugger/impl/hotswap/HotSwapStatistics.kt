// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.hotswap.HotSwapSource
import org.jetbrains.annotations.ApiStatus

@Suppress("PublicApiImplicitType")
@ApiStatus.Internal
object HotSwapStatistics : CounterUsagesCollector() {
  private val group = EventLogGroup("debugger.hotswap", 4)

  private val dcevmEnabled = EventFields.Boolean("dcevm_enabled")

  private val hotSwapSource = EventFields.Enum<HotSwapSource>("source")
  private val hotSwapStatus = EventFields.Enum<HotSwapStatus>("status")

  private val hotSwapResult = group.registerEvent("hotswap.finished", hotSwapStatus, hotSwapSource)
  private val hotSwapFailureReason = group.registerEvent("hotswap.failed", EventFields.Enum<HotSwapFailureReason>("reason"), hotSwapSource)
  private val hotSwapClassesNumber = group.registerEvent("hotswap.classes.reloaded", EventFields.Int("count"))
  private val sessionStarted = group.registerEvent("session.started", dcevmEnabled)
  private val sourceCompatibilityDetected = group.registerEvent(
    "source.compatibility.detected",
    EventFields.Enum<HotSwapSourceCompatibilityStatus>("status"),
  )


  override fun getGroup(): EventLogGroup = group

  @JvmStatic
  fun logSessionStarted(project: Project, dcevmEnabled: Boolean) {
    sessionStarted.log(project, dcevmEnabled)
  }

  @JvmStatic
  fun logHotSwapResult(project: Project, status: HotSwapStatus, source: HotSwapSource?) {
    hotSwapResult.log(project, status, source ?: HotSwapSource.UNKNOWN)
  }

  @JvmStatic
  fun logFailureReason(project: Project, reason: HotSwapFailureReason, source: HotSwapSource?) {
    hotSwapFailureReason.log(project, reason, source ?: HotSwapSource.UNKNOWN)
  }

  @JvmStatic
  fun logClassesReloaded(project: Project, count: Int) = hotSwapClassesNumber.log(project, count)

  @JvmStatic
  fun logSourceCompatibilityDetected(project: Project, status: HotSwapSourceCompatibilityStatus) {
    sourceCompatibilityDetected.log(project, status)
  }

  enum class HotSwapStatus {
    SUCCESS,
    COMPILATION_FAILURE,
    HOT_SWAP_FAILURE,
    NO_CHANGES,
    RESTART,
  }

  enum class HotSwapSourceCompatibilityStatus {
    COMPATIBLE,
    UNSUPPORTED, // no checker for the modified file's langauge
    NO_CHECKS, // checkers aren't enabled (feature not implemented/DCEVM enabled)
    CHECK_IMPOSSIBLE, // cannot access previous content (VCS issue or performance limitation)
    UNKNOWN, // checker failed
    INCOMPATIBLE,
  }
}

@ApiStatus.Internal
enum class HotSwapFailureReason {
  METHOD_ADDED,
  METHOD_REMOVED,
  SIGNATURE_MODIFIED,
  STRUCTURE_MODIFIED,
  CLASS_MODIFIERS_CHANGED,
  CLASS_ATTRIBUTES_CHANGED,
  METHOD_MODIFIERS_CHANGED,
  OTHER,
}