// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vsmac.mappings

import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.BundledLookAndFeel

object ThemesMappings {
  fun themeMap(theme: String): BundledLookAndFeel? = when (theme) {
    "Dark" -> KnownLafs.Darcula
    "Light" -> KnownLafs.Light
    else -> null
  }
}