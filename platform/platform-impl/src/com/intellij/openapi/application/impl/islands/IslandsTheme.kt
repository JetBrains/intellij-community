// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl.islands

import com.intellij.ide.ui.LafManager

internal object IslandsTheme {
  fun isIslandTheme(): Boolean {
    return isIslandTheme(LafManager.getInstance().currentUIThemeLookAndFeel?.id ?: return false)
  }

  fun isIslandTheme(themeId: String): Boolean = themeId == "Islands Dark" || themeId == "Islands Light" || themeId == "Islands Darcula"
}
