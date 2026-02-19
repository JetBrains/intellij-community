// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.BooleanEventField
import com.intellij.internal.statistic.eventLog.events.ClassEventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object RefactoringUsageCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("refactoring", 4)

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
  val USAGES_SEARCHED = GROUP.registerEvent("usages.searched", PROCESSOR, CANCELLED, EventFields.DurationMs)

  @JvmField
  val EXECUTED = GROUP.registerEvent("executed", PROCESSOR, EventFields.DurationMs)


  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}