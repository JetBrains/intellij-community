// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.db

import com.intellij.ide.startup.importSettings.providers.vswin.utilities.RunConfigurationEnvironmentUtils
import java.util.*

object WindowsEnvVariables {
  /*
 * Some widely used env vars.
 */
  val applicationData: String = get("APPDATA").toString()
  val localApplicationData: String = get("LOCALAPPDATA").toString()
  val userProfile: String = get("USERPROFILE").toString()

  /**
   * Function to get one Windows env var.
   *
   * Use without %%. Example: get("APPDATA")
   */
  fun get(variable: String): String? = System.getenv()[variable] ?: System.getenv()[variable.uppercase(Locale.getDefault())]

  fun expandPath(path: String): String? = RunConfigurationEnvironmentUtils.expandVariables("%", "%", path, WindowsEnvVariables::get, false, null)
}