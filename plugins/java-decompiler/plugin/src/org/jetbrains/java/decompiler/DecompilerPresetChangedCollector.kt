// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.java.decompiler

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId1
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object DecompilerPresetChangedCollector : CounterUsagesCollector() {
  private val GROUP: EventLogGroup = EventLogGroup(
    id = "java.decompiler",
    version = 1,
    recorder = "FUS",
    description = "This group contains events originating from the Java Bytecode Decompiler plugin.",
  )

  private val DECOMPILER_PRESET_CHANGED: EventId1<DecompilerPreset> = GROUP.registerEvent(
    eventId = "decompiler.preset.changed",
    eventField1 = EventFields.Enum("preset", DecompilerPreset::class.java),
    description = "This event is collected whenever the user manually changes the decompiler preset while viewing decompiled bytecode.",
  )

  override fun getGroup(): EventLogGroup = GROUP

  fun decompilerPresetChanged(preset: DecompilerPreset) {
    DECOMPILER_PRESET_CHANGED.log(preset)
  }
}
