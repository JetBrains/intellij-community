// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.execution.wsl.WSLDistribution
import com.intellij.openapi.diagnostic.Logger

private val LOGGER = Logger.getInstance("WslTeamCityTestTool")

/**
 * Early failure for IJI-1731
 */
fun ensureCorrectVersion(wsl: WSLDistribution) {
  System.getenv("WSL_VERSION")?.let { // This var must be set on TC manually
    if (it.toInt() != wsl.version) {
      val error = """
        Variable provided by environment claims WSL is $it.
        But wsl is ${wsl.version}.
        Hence, environment provides wrong information.
        With all of that, test can't continue. Fix your environment.
      """.trimIndent()
      LOGGER.error(error)
      error(error)
    }
  }
}

