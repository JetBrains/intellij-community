// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.*
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class RefactoringUsageCollector : CounterUsagesCollector() {
  companion object {
    private val GROUP = EventLogGroup("refactoring", 3)

    @JvmField
    val HANDLER: ClassEventField = EventFields.Class("handler")

    @JvmField
    val ELEMENT: ClassEventField = EventFields.Class("element")

    @JvmField
    val PROCESSOR: ClassEventField = EventFields.Class("processor")

    @JvmField
    val CANCELLED: BooleanEventField = EventFields.Boolean("cancelled")

    @JvmField
    val HANDLER_INVOKED: VarargEventId = GROUP.registerVarargEvent("handler.invoked", EventFields.Language, HANDLER, ELEMENT)

    @JvmField
    val USAGES_SEARCHED = GROUP.registerEvent("usages_searched", PROCESSOR, CANCELLED, EventFields.DurationMs)

    @JvmField
    val EXECUTED = GROUP.registerEvent("executed", PROCESSOR, EventFields.DurationMs)

  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}