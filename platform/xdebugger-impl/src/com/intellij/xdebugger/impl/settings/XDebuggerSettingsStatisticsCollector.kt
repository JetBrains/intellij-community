// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.settings

import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector

internal class XDebuggerSettingsStatisticsCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("xdebugger.settings.ide", 1)
  private val SHOW_ALL_FRAMES = GROUP.registerVarargEvent("show.all.frames", EventFields.Enabled)

  override fun getGroup(): EventLogGroup = GROUP

  override fun getMetrics() = buildSet {
    val dvSettings = XDebuggerSettingManagerImpl.getInstanceImpl().dataViewSettings
    val dvDefaults = XDebuggerDataViewSettings()

    addBoolIfDiffers(this, dvSettings, dvDefaults, { it.isShowLibraryStackFrames }, SHOW_ALL_FRAMES)
  }
}