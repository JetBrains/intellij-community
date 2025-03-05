// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object DecompilerPresetChangedCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup("java.decompiler", 1)

  private val DECOMPILER_PRESET_CHANGED: EventId1<DecompilerPreset> = GROUP.registerEvent(
    eventId = "decompiler.preset.changed",
    eventField1 = EventFields.Enum("preset", DecompilerPreset::class.java),
  )

  override fun getGroup(): EventLogGroup = GROUP

  fun decompilerPresetChanged(preset: DecompilerPreset) {
    DECOMPILER_PRESET_CHANGED.log(preset)
  }
}
