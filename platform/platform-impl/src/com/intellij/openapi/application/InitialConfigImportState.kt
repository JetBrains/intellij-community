// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.GeneralSettings
import com.intellij.idea.AppMode
import com.intellij.openapi.application.CustomConfigMigrationOption.SetProperties
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

@ApiStatus.Internal
object InitialConfigImportState {
  const val FIRST_SESSION_KEY: String = "intellij.first.ide.session"
  const val CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY: String = "intellij.config.imported.in.current.session"
  const val CUSTOM_MARKER_FILE_NAME: String = "migrate.config"
  const val FRONTEND_PLUGINS_TO_MIGRATE_DIR_NAME: String = "frontend-to-migrate"
  const val MIGRATION_INSTALLED_PLUGINS_TXT: String = "migration_installed_plugins.txt"

  // copy of `ConfigImportHelper#OPTIONS`
  private val OPTIONS: Array<String> = arrayOf(
    PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE,
    PathManager.OPTIONS_DIRECTORY + '/' + GeneralSettings.IDE_GENERAL_XML,
    PathManager.OPTIONS_DIRECTORY + "/options.xml",
  )

  /**
   * Returns `true` when the IDE is launched for the first time (i.e., there was no config directory).
   **/
  @JvmStatic
  fun isFirstSession(): Boolean = System.getProperty(FIRST_SESSION_KEY).toBoolean()

  /**
   * Returns `true` when the IDE is launched for the first time, and configs were imported from another installation.
   */
  @JvmStatic
  fun isConfigImported(): Boolean = System.getProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY).toBoolean()

  /**
   * Checking that the current user is a "new" one (i.e., this is the very first launch of the IDE on this machine).
   */
  @JvmStatic
  fun isNewUser(): Boolean = isFirstSession() && !isConfigImported()

  @Deprecated(message = "Use `ConfigImportHelper.isConfigDirectory()` instead")
  fun isConfigDirectory(candidate: Path): Boolean = OPTIONS.any { Files.exists(candidate.resolve(it)) }

  @JvmStatic
  fun isStartupWizardEnabled(): Boolean =
    !AppMode.isRemoteDevHost() &&
    System.getProperty("intellij.startup.wizard", if (ApplicationManagerEx.isInIntegrationTest()) "false" else "true").toBoolean()

  @JvmStatic
  @Throws(IOException::class)
  fun writeOptionsForRestart(newConfigDir: Path) {
    val properties = ArrayList<String>()
    properties.add(FIRST_SESSION_KEY)
    if (isConfigImported()) {
      properties.add(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY)
    }
    SetProperties(properties).writeConfigMarkerFile(newConfigDir)
  }
}
