// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vsmac.mappings

import com.intellij.ide.startup.importSettings.db.KnownColorSchemes
import com.intellij.ide.startup.importSettings.models.BundledEditorColorScheme


object SchemesMappings {
  fun schemeMap(scheme: String): BundledEditorColorScheme = when (scheme) {
    "Light" -> KnownColorSchemes.Light
    "Dark" -> KnownColorSchemes.Darcula
    "Gruvbox" -> KnownColorSchemes.Darcula
    "High Contrast Dark" -> KnownColorSchemes.HighContrast
    "High Contrast Light" -> KnownColorSchemes.Light
    "Monokai" -> KnownColorSchemes.Darcula
    "Nightshade" -> KnownColorSchemes.HighContrast
    "Oblivion" -> KnownColorSchemes.Darcula
    "Solarized Dark" -> KnownColorSchemes.Darcula
    "Solarized Light" -> KnownColorSchemes.Light
    "Tango" -> KnownColorSchemes.Light
    "Legacy – Dark" -> KnownColorSchemes.Darcula
    "Legacy – Light" -> KnownColorSchemes.Light
    else -> if (scheme.lowercase().endsWith("light")) KnownColorSchemes.Light else KnownColorSchemes.Darcula
  }
}