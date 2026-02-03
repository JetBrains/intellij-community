// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.db

import com.intellij.ide.startup.importSettings.providers.vswin.utilities.RunConfigurationEnvironmentUtils
import com.intellij.util.EnvironmentUtil

object WindowsEnvVariables {
  /*
   * Some widely used env vars.
   */
  val applicationData: String = EnvironmentUtil.getValue("APPDATA").toString()
  val localApplicationData: String = EnvironmentUtil.getValue("LOCALAPPDATA").toString()
  val userProfile: String = EnvironmentUtil.getValue("USERPROFILE").toString()

  fun expandPath(path: String): String? =
    RunConfigurationEnvironmentUtils.expandVariables("%", "%", path, EnvironmentUtil::getValue, false, null)
}
