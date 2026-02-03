// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.parsers.data

import com.intellij.ide.startup.importSettings.providers.vswin.mappings.FontsAndColorsMappings

class FontsAndColorsParsedData(themeUuid: String) : VSParsedData {
  companion object {
    const val key: String = "Environment_FontsAndColors"
  }

  val theme: FontsAndColorsMappings.VsTheme = FontsAndColorsMappings.VsTheme.fromString(themeUuid)
}