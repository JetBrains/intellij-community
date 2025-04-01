// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.TransferableIdeId
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.VSMacSettingsProcessor.Companion.getGeneralSettingsFile
import com.intellij.ide.startup.importSettings.transfer.backend.providers.vsmac.VSMacSettingsProcessor.Companion.vsPreferences
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.util.SmartList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.util.Date
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.isDirectory
import kotlin.io.path.isHidden
import kotlin.io.path.listDirectoryEntries

private val logger = logger<VSMacTransferSettingsProvider>()

class VSMacTransferSettingsProvider : TransferSettingsProvider {

  override val transferableIdeId: TransferableIdeId = TransferableIdeId.VisualStudioForMac
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

    var max = Instant.MIN
    var lastUsedVersion: String? = null
    for (path in pathToDir.listDirectoryEntries()) {
      if (!path.isDirectory() && path.isHidden()) continue

      val maybeVersion = path.fileName.toString()
      val recentlyUsedFile = getGeneralSettingsFile(maybeVersion)

      if (recentlyUsedFile.exists()) {
        val lastModificationTime = recentlyUsedFile.getLastModifiedTime().toInstant()
        if (max < lastModificationTime) {
          max = lastModificationTime
          lastUsedVersion = maybeVersion
        }
      }
    }

    return lastUsedVersion
  }

  private fun getLastUsed(version: String): Date? {
    val recentlyUsedFile = getGeneralSettingsFile(version)

    return try {
      Date.from(recentlyUsedFile.getLastModifiedTime().toInstant())
    }
    catch (t: Throwable) {
      logger.warn(t)
      null
    }
  }

}
