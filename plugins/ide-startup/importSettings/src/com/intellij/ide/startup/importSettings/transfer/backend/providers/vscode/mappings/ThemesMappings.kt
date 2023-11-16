// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vscode.mappings

import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.BundledLookAndFeel

object ThemesMappings {
  fun themeMap(theme: String): BundledLookAndFeel = when (theme) {
    "vs" -> KnownLafs.Light
    "vs-dark" -> KnownLafs.Darcula
    "hc-black" -> KnownLafs.HighContrast
    "Monokai" -> KnownLafs.Darcula //KnownLafs.MonokaiSpectrum
    "Solarized Dark" -> KnownLafs.Darcula //KnownLafs.SolarizedDark
    "Solarized Light" -> KnownLafs.Light //KnownLafs.SolarizedLight
    else -> otherThemeConverter(theme)
  }

  private fun otherThemeConverter(theme: String) = if (theme.lowercase().contains("light")) KnownLafs.Light else KnownLafs.Darcula
}