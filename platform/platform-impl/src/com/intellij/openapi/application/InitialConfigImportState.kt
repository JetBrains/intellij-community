// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.ide.GeneralSettings
import com.intellij.idea.AppMode
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.components.StoragePathMacros
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.Path

internal const val FIRST_SESSION_KEY = "intellij.first.ide.session"
internal const val CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session"

@ApiStatus.Internal
object InitialConfigImportState {
  const val CUSTOM_MARKER_FILE_NAME: String = "migrate.config"
  const val FRONTEND_PLUGINS_TO_MIGRATE_DIR_NAME: String = "frontend-to-migrate"

  internal val OPTIONS: Array<String> = arrayOf(
    PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE,
    PathManager.OPTIONS_DIRECTORY + '/' + GeneralSettings.IDE_GENERAL_XML,
    PathManager.OPTIONS_DIRECTORY + "/options.xml",
  )


  /**
   * Returns `true` when the IDE is launched for the first time (i.e., there was no config directory).
   **/
  fun isFirstSession(): Boolean = System.getProperty(FIRST_SESSION_KEY).toBoolean()

  /**
   * Returns `true` when the IDE is launched for the first time, and configs were imported from another installation.
   */
  fun isConfigImported(): Boolean = System.getProperty(CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY).toBoolean()

  /**
   * Checking that the current user is a "new" one (i.e., this is the very first launch of the IDE on this machine).
   */
  fun isNewUser(): Boolean = isFirstSession() && !isConfigImported()

  fun isConfigDirectory(candidate: Path): Boolean {
    for (t in OPTIONS) {
      if (Files.exists(candidate.resolve(t))) {
        return true
      }
    }
    return false
  }

  fun isStartupWizardEnabled(): Boolean {
    return (!ApplicationManagerEx.isInIntegrationTest() || java.lang.Boolean.getBoolean("show.wizard.in.test")) &&
           !AppMode.isRemoteDevHost() &&
           System.getProperty("intellij.startup.wizard", "true").toBoolean()
  }
}