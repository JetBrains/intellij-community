// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.providers.vswin.utilities

import com.intellij.ide.startup.importSettings.db.WindowsEnvVariables
import com.intellij.openapi.diagnostic.logger


class VSProfileSettingsFileNotFound(msg: String, cause: Throwable? = null) : Exception(msg, cause)

object VSProfileDetectorUtils {
  const val vsInstallDir: String = "vsspv_vs_install_directory"
  private const val vsLocation = "vsspv_visualstudio_dir"
  private const val userProfile = "vsspv_user_appdata"
  private const val localAppdata = "vsspv_vs_localappdata_dir"

  private val logger = logger<VSProfileDetectorUtils>()

  fun expandPath(str: String, hive: VSHive): String? {
    val localAppDataLoc = "${WindowsEnvVariables.localApplicationData}\\Microsoft\\VisualStudio\\${hive.hiveString}"

    val map = mapOf(
      vsLocation to hive.registry?.vsLocation,
      localAppdata to localAppDataLoc,
      userProfile to WindowsEnvVariables.userProfile
    )

    val preExp = RunConfigurationEnvironmentUtils.expandVariables(str, map, emptyMap())
    requireNotNull(preExp)

    return WindowsEnvVariables.expandPath(preExp)
  }

  fun rootSuffixStabilizer(hive: VSHive): String? {
    val rootSuffix = hive.rootSuffix ?: return null

    return when (rootSuffix.lowercase()) {
      "exp" -> "Experimental"
      else -> rootSuffix
    }
  }
}