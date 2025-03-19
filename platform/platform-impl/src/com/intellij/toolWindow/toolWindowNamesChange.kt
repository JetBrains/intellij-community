// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.ide.util.PropertiesComponent
import org.jetbrains.annotations.ApiStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val LAST_TOOLWINDOW_NAME_TOGGLE_EVENT_TS = "toolwindow.showToolWindowsNames.ts"
private const val LAST_TOOLWINDOW_NAME_TOGGLE_EVENT_VALUE = "toolwindow.showToolWindowsNames.value"

// used for survey trigger
internal fun showToolWindowNamesChanged(value: Boolean) {
  val properties = PropertiesComponent.getInstance()
  val ts = LocalDateTime.now()
  properties.setValue(LAST_TOOLWINDOW_NAME_TOGGLE_EVENT_TS, ts.format(DateTimeFormatter.ISO_DATE_TIME))
  properties.setValue(LAST_TOOLWINDOW_NAME_TOGGLE_EVENT_VALUE, value)
}

@ApiStatus.Internal
fun getLastToolWindowNameToggleEvent(): Pair<LocalDateTime, Boolean>? {
  val properties = PropertiesComponent.getInstance()

  val ts = properties.getValue(LAST_TOOLWINDOW_NAME_TOGGLE_EVENT_TS)
    ?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ISO_DATE_TIME) }.getOrNull() }
  val value = properties.getBoolean(LAST_TOOLWINDOW_NAME_TOGGLE_EVENT_VALUE)

  if (ts != null) return ts to value

  return null
}