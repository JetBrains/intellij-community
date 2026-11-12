// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities

import com.intellij.ide.startup.importSettings.providers.vswin.utilities.Version2
import com.intellij.openapi.util.NlsSafe

/**
 * The product name of a Visual Studio instance.
 */
sealed interface VSProductName {
  val text: @NlsSafe String

  /**
   * The title from the Visual Studio Installer, for example "Visual Studio Professional 2026".
   * The title includes the edition, e.g. "Professional".
   */
  class InstallerTitle(override val text: @NlsSafe String) : VSProductName

  /**
   * The legacy mapped name from the VS version, for example "Visual Studio 2015".
   * The name doesn't include the edition.
   */
  class FromVersion(version: Version2) : VSProductName {
    override val text: @NlsSafe String = "Visual Studio ${productVersionTextRepresentation(version)}"

    private fun productVersionTextRepresentation(version: Version2): String {
      return when (version.major) {
        17 -> "2022"
        16 -> "2019"
        15 -> "2017"
        14 -> "2015"
        12 -> "2013"
        11 -> "2012"
        else -> "Ver. ${version.major}.${version.minor}"
      }
    }
  }
}
