// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vscode.mappings

import com.intellij.ide.startup.importSettings.db.KnownColorSchemes
import com.intellij.ide.startup.importSettings.models.BundledEditorColorScheme

object SchemesMappings {
  fun schemeMap(scheme: String): BundledEditorColorScheme = when (scheme) {
    "vs" -> KnownColorSchemes.Light
    "vs-dark" -> KnownColorSchemes.Darcula
    "hc-black" -> KnownColorSchemes.HighContrast
    "Monokai" -> KnownColorSchemes.Darcula //KnownColorSchemes.Monokai
    "Solarized Dark" -> KnownColorSchemes.Darcula //KnownColorSchemes.SolarizedDark
    "Solarized Light" -> KnownColorSchemes.Light //KnownColorSchemes.SolarizedLight
    else -> otherSchemeConverter(scheme)
  }

  private fun otherSchemeConverter(scheme: String) = if (scheme.lowercase().contains("light")) KnownColorSchemes.Light
  else KnownColorSchemes.Darcula
}