// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar

import com.intellij.ide.ui.MainMenuDisplayMode
import com.intellij.ide.ui.UISettings
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class ShowMode {
  MENU, TOOLBAR, TOOLBAR_WITH_MENU;

  companion object {
    fun getCurrent(): ShowMode {
      val mainMenuDisplayMode = UISettings.getInstance().mainMenuDisplayMode
      return getShowMode(mainMenuDisplayMode)
    }

    fun getShowMode(mainMenuDisplayMode: MainMenuDisplayMode): ShowMode = when (mainMenuDisplayMode) {
      MainMenuDisplayMode.MERGED_WITH_MAIN_TOOLBAR -> TOOLBAR_WITH_MENU
      MainMenuDisplayMode.UNDER_HAMBURGER_BUTTON -> TOOLBAR
      else -> MENU
    }
  }
}