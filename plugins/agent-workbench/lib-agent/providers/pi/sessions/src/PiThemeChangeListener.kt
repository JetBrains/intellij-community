// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.pi.sessions

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme

internal class PiThemeChangeListener : EditorColorsListener, LafManagerListener {
  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    PiThemeSupport.DEFAULT.syncCurrentThemeState()
  }

  override fun lookAndFeelChanged(source: LafManager) {
    PiThemeSupport.DEFAULT.syncCurrentThemeState()
  }
}
