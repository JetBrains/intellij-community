// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import com.intellij.ide.startup.importSettings.providers.vsmac.VSMacSettingsProcessor
import com.intellij.ide.startup.importSettings.providers.vsmac.VSMacSettingsProcessor.Companion.getGeneralSettingsFile
import com.intellij.ide.startup.importSettings.providers.vsmac.VSMacSettingsProcessor.Companion.vsPreferences
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SmartList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.Path

private val logger = logger<VSMacTransferSettingsProvider>()

class VSMacTransferSettingsProvider : TransferSettingsProvider {

  override val transferableIdeId = TransferableIdeId.VisualStudioForMac
  override val name: String = "Visual Studio for Mac"

  override fun isAvailable(): Boolean = SystemInfoRt.isMac

  override suspend fun hasDataToImport(): Boolean =
    withContext(Dispatchers.IO) {
      Files.isDirectory(Path(vsPreferences))
    }

  override fun getIdeVersions(skipIds: List<String>): SmartList<IdeVersion> = when (val version = detectVSForMacVersion()) {
    null -> SmartList()
    else -> SmartList(getIdeVersion(version))
  }

  private fun getIdeVersion(version: String) = IdeVersion(
    transferableId = transferableIdeId,
    transferableVersion = null,
    name = "Visual Studio for Mac",
    id = "VSMAC",
    icon = AllIcons.TransferSettings.Vsmac,
    lastUsed = getLastUsed(version),
    settingsInit = { VSMacSettingsProcessor().getProcessedSettings(version) },
    provider = this
  )

  private fun detectVSForMacVersion(): String? {
    val pathToDir = Paths.get(vsPreferences)

    if (!Files.isDirectory(pathToDir)) {
      return null
    }

    var max = 0L
    var lastUsedVersion: String? = null
    Files.list(pathToDir).use { files ->
      for (path in files) {
        if (!Files.isDirectory(path) && Files.isHidden(path)) continue

        val maybeVersion = path.fileName.toString()
        val recentlyUsedFile = getGeneralSettingsFile(maybeVersion)

        if (recentlyUsedFile.exists()) {
          val lastModificationTime = recentlyUsedFile.lastModified()
          if (max < lastModificationTime) {
            max = lastModificationTime
            lastUsedVersion = maybeVersion
          }
        }
      }
    }

    return lastUsedVersion
  }

  private fun getLastUsed(version: String): Date? {
    val recentlyUsedFile = getGeneralSettingsFile(version)

    return try {
      Date(recentlyUsedFile.lastModified())
    }
    catch (t: Throwable) {
      logger.warn(t)
      null
    }
  }

}
