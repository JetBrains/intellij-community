// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.jetbrains.annotations.ApiStatus

internal const val FIRST_SESSION_KEY = "intellij.first.ide.session"
internal const val CONFIG_IMPORTED_IN_CURRENT_SESSION_KEY = "intellij.config.imported.in.current.session"

@ApiStatus.Internal
object InitialConfigImportState {
  const val CUSTOM_MARKER_FILE_NAME: String = "migrate.config"

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
}