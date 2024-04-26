// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vsmac

import com.intellij.ide.startup.importSettings.db.KnownColorSchemes
import com.intellij.ide.startup.importSettings.db.KnownKeymaps
import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.providers.vsmac.parsers.GeneralSettingsParser
import com.intellij.ide.startup.importSettings.providers.vsmac.parsers.KeyBindingsParser
import com.intellij.ide.startup.importSettings.providers.vsmac.parsers.RecentProjectsParser
import java.io.File

class VSMacSettingsProcessor {
  companion object {
    private val homeDirectory = System.getProperty("user.home")

    private val vsHome = "$homeDirectory/Library/VisualStudio"
    internal val vsPreferences: String = "$homeDirectory/Library/Preferences/VisualStudio"

    internal fun getRecentlyUsedFile(version: String): File = File("$vsPreferences/$version/RecentlyUsed.xml")
    internal fun getKeyBindingsFile(version: String): File = File("$vsHome/$version/KeyBindings/Custom.mac-kb.xml")
    internal fun getGeneralSettingsFile(version: String): File = File("$vsPreferences/$version/MonoDevelopProperties.xml")

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