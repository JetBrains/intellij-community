// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

internal object IdeScriptEngineUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP

  private val GROUP = EventLogGroup("ide.script.engine", 3)
  private val USED = GROUP.registerEvent("used", EventFields.Class("factory"))

  @JvmStatic
  fun logUsageEvent(clazz: Class<*>): Unit = USED.log(clazz)
}