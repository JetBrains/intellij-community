// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.openapi.application.UI
import com.intellij.util.ui.StartupUiUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.AWTEvent
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowEvent

internal class WaylandAppIconPatcher(coroutineScope: CoroutineScope) {
  init {
    if (StartupUiUtil.isWaylandToolkit()) {
      coroutineScope.launch(Dispatchers.UI) {
        Toolkit.getDefaultToolkit().addAWTEventListener(::dispatchEvent, AWTEvent.WINDOW_EVENT_MASK)
      }
    }
  }

  private fun dispatchEvent(event: AWTEvent) {
    val window = event.source as? Window ?: return

    if (event.id == WindowEvent.WINDOW_OPENED) {
      AppUIUtil.updateAppWindowIcon(window)
    }
  }
}
