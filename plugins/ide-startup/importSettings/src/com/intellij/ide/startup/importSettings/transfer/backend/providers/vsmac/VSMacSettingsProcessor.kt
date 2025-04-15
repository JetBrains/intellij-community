// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac

import com.intellij.ide.startup.importSettings.db.KnownKeymaps
import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.transfer.backend.db.KnownColorSchemes
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.parsers.GeneralSettingsParser
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.parsers.KeyBindingsParser
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.parsers.RecentProjectsParser
import java.nio.file.Path
import kotlin.io.path.Path

class VSMacSettingsProcessor {
  companion object {
    private val homeDirectory = System.getProperty("user.home")

    private val vsHome = "$homeDirectory/Library/VisualStudio"
    internal val vsPreferences: String = "$homeDirectory/Library/Preferences/VisualStudio"

    internal fun getRecentlyUsedFile(version: String): Path = Path("$vsPreferences/$version/RecentlyUsed.xml")
    internal fun getKeyBindingsFile(version: String): Path = Path("$vsHome/$version/KeyBindings/Custom.mac-kb.xml")
    internal fun getGeneralSettingsFile(version: String): Path = Path("$vsPreferences/$version/MonoDevelopProperties.xml")

    fun getDefaultSettings(): Settings = Settings(
      laf = KnownLafs.Light,
      syntaxScheme = KnownColorSchemes.Light,
      keymap = KnownKeymaps.VSMac
    )
  }

  fun getProcessedSettings(version: String): Settings {
    val keyBindingsFile = getKeyBindingsFile(version)
    val generalSettingsFile = getGeneralSettingsFile(version)
    val recentlyUsedFile = getRecentlyUsedFile(version)

    val settings = getDefaultSettings()
    KeyBindingsParser(settings).process(keyBindingsFile)
    GeneralSettingsParser(settings).process(generalSettingsFile)
    RecentProjectsParser(settings).process(recentlyUsedFile)

    return settings
  }
}