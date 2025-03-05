// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.eventLog

import com.intellij.internal.statistic.config.EventLogOptions

data class MachineId(val id: String, val revision: Int) {
  companion object {
    @JvmField val DISABLED: MachineId = MachineId(EventLogOptions.MACHINE_ID_DISABLED, EventLogOptions.DEFAULT_ID_REVISION)
    @JvmField val UNKNOWN: MachineId = MachineId(EventLogOptions.MACHINE_ID_UNKNOWN, EventLogOptions.DEFAULT_ID_REVISION)
  }
}
