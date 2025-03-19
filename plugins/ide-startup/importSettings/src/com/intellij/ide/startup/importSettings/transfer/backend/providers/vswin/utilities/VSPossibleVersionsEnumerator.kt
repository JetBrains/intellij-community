// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vswin.utilities

import com.intellij.ide.startup.importSettings.db.WindowsEnvVariables
import com.intellij.openapi.diagnostic.logger
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import kotlin.io.path.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class VSPossibleVersionsEnumerator {

  fun get(): List<VSHive> {
    return (enumOldPossibleVersions() + enumNewPossibleVersions()).apply {
      logger.info("List of potential ides: ${this.joinToString(",") { it.hiveString }}")
    }
  }

  fun hasAny(): Boolean {
    return enumNewPossibleVersions().isNotEmpty() || enumOldPossibleVersions().isNotEmpty()
  }

  private fun enumOldPossibleVersions(): List<VSHive> {
    val registry = try {
      val key = "SOFTWARE\\Microsoft\\VisualStudio"
      if (Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, key)) {
        Advapi32Util.registryGetKeys(WinReg.HKEY_CURRENT_USER, key)
      } else {
        emptyArray()
      }
    }
    catch (t: Throwable) {
      logger.warn(t)
      return emptyList()
    }

    if (registry == null) {
      logger.info("No old vs found (no registry keys)")
      return emptyList()
    }

    return registry.mapNotNull { VSHive.parse(it, VSHive.Types.Old) }
  }

  private fun enumNewPossibleVersions(): List<VSHive> {
    val dir = Path(WindowsEnvVariables.localApplicationData, "Microsoft\\VisualStudio")

    if (!dir.isDirectory()) {
      return emptyList()
    }

    return try {
      dir.listDirectoryEntries().mapNotNull { VSHive.parse(it.name, VSHive.Types.New) }
    }
    catch (t: Throwable) {
      logger.warn("Failed to read \"$dir\".", t)
      emptyList()
    }

  }
}

private val logger = logger<VSPossibleVersionsEnumerator>()
