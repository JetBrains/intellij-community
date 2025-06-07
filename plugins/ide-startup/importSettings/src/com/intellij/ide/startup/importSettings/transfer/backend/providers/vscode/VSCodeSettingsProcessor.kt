// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vscode

import com.intellij.ide.startup.importSettings.db.KnownKeymaps
import com.intellij.ide.startup.importSettings.db.KnownLafs
import com.intellij.ide.startup.importSettings.db.WindowsEnvVariables
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.transfer.backend.db.KnownColorSchemes
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vscode.parsers.*
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.SystemInfoRt
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant

open class VSCodeSettingsProcessor(private val scope: CoroutineScope, appFolder: String = "Code", pluginFolder: String = ".vscode") {
  private val homeDirectory = System.getProperty("user.home")

  internal val vsCodeHome: String = when {
    SystemInfo.isMac -> "$homeDirectory/Library/Application Support/$appFolder"
    SystemInfo.isWindows -> "${WindowsEnvVariables.applicationData}/$appFolder"
    else -> "$homeDirectory/.config/$appFolder"
  }

  internal val storageFile: File = File("$vsCodeHome/storage.json")
  internal val keyBindingsFile: File = File("$vsCodeHome/User/keybindings.json")
  internal val generalSettingsFile: File = File("$vsCodeHome/User/settings.json")
  internal val pluginsDirectory: File = File("$homeDirectory/$pluginFolder/extensions")
  internal val database: File = File("$vsCodeHome/User/globalStorage/state.vscdb")

  fun getDefaultSettings(): Settings = Settings(
    laf = KnownLafs.Darcula,
    syntaxScheme = KnownColorSchemes.Darcula,
    keymap = if (SystemInfoRt.isMac) KnownKeymaps.VSCodeMac else KnownKeymaps.VSCode
  )

  private val timeAfterLastModificationToConsiderTheInstanceRecent = Duration.ofHours(365 * 24) // one year

  fun willDetectAtLeastSomething(): Boolean {
    if (generalSettingsFile.exists())
      return true

    if (!pluginsDirectory.exists() || !pluginsDirectory.isDirectory)
      return false
    val pluginsDirEntries = pluginsDirectory.listFiles() ?: return false  // no extensions and config file

    for (pluginDirEntry in pluginsDirEntries) {
      if (pluginDirEntry.isDirectory)
        return true
    }
    return false
  }

  fun isInstanceRecentEnough(): Boolean {
    try {
      val fileToCheck = database
      if (fileToCheck.exists()) {
        val time = Files.getLastModifiedTime(fileToCheck.toPath())
        return time.toInstant() > Instant.now() - timeAfterLastModificationToConsiderTheInstanceRecent
      }

      return false
    }
    catch (_: IOException) {
      return false
    }
  }

  open fun getProcessedSettings(): Settings {
    val settings = getDefaultSettings()
    if (keyBindingsFile.exists()) {
      KeyBindingsParser(settings).process(keyBindingsFile)
    }
    if (pluginsDirectory.exists()) {
      PluginParser(settings).process(pluginsDirectory)
    }
    if (storageFile.exists()) {
      StorageParser(settings).process(storageFile)
    }
    if (generalSettingsFile.exists()) {
      GeneralSettingsParser(settings).process(generalSettingsFile)
    }
    if (database.exists()) {
      StateDatabaseParser(scope, settings).process(database)
    }

    return settings
  }

}