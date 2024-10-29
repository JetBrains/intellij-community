// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@Suppress("PublicApiImplicitType")
@ApiStatus.Internal
object HotSwapStatistics : CounterUsagesCollector() {
  private val group = EventLogGroup("debugger.hotswap", 1)

  private val hotSwapCalled = group.registerEvent("hotswap.called", EventFields.Enum<HotSwapSource>("source"))
  private val hotSwapStatus = group.registerEvent("hotswap.finished", EventFields.Enum<HotSwapStatus>("status"))
  private val hotSwapFailureReason = group.registerEvent("hotswap.failed", EventFields.Enum<HotSwapFailureReason>("reason"))
  private val hotSwapClassesNumber = group.registerEvent("hotswap.classes.reloaded", EventFields.Int("count"))

  override fun getGroup(): EventLogGroup = group

  @JvmStatic
  fun logHotSwapCalled(project: Project, source: HotSwapSource) = hotSwapCalled.log(project, source)

  @JvmStatic
  fun logHotSwapStatus(project: Project, status: HotSwapStatus) = hotSwapStatus.log(project, status)

  @JvmStatic
  fun logFailureReason(project: Project, reason: HotSwapFailureReason) = hotSwapFailureReason.log(project, reason)

  @JvmStatic
  fun logClassesReloaded(project: Project, count: Int) = hotSwapClassesNumber.log(project, count)

  enum class HotSwapSource {
    RELOAD_FILE,
    RELOAD_ALL,
    ON_REBUILD_AUTO,
    ON_REBUILD_ASK,
    RELOAD_MODIFIED_ACTION,
    RELOAD_MODIFIED_BUTTON,
  }

  enum class HotSwapStatus {
    SUCCESS,
    COMPILATION_FAILURE,
    HOT_SWAP_FAILURE,
    NO_CHANGES,
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