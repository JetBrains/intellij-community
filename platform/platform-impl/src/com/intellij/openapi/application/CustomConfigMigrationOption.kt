// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private val log = logger<CustomConfigMigrationOption>()

sealed class CustomConfigMigrationOption {

  object StartWithCleanConfig : CustomConfigMigrationOption()

  class MigrateFromCustomPlace(val location: Path) : CustomConfigMigrationOption()

  companion object {
    @JvmStatic
    fun readCustomConfigMigrationOptionAndRemoveMarkerFile(): CustomConfigMigrationOption? {
      val markerFile = getMarkerFilePath()
      if (!markerFile.exists()) return null

      try {
        val content = FileUtil.loadFile(markerFile.toFile())
        if (content.isEmpty()) return StartWithCleanConfig
        val configToMigrate = Paths.get(content)
        if (!configToMigrate.exists()) {
          log.warn("$markerFile points to non-existent config: [$content]")
          return null
        }
        return MigrateFromCustomPlace(configToMigrate)
      }
      catch (e: Exception) {
        log.warn("Couldn't load content of $markerFile")
        return null
      }
      finally {
        removeMarkerFile(markerFile)
      }
    }

    @JvmStatic
    fun needsCustomConfigMigration(): Boolean = Files.exists(getMarkerFilePath())

    private fun removeMarkerFile(markerFile: Path) {
      try {
        markerFile.delete()
      }
      catch (e: Exception) {
        log.warn("Couldn't delete the custom config migration file $markerFile", e)
      }
    }

    /**
     * `null` means starts with clean configs
     */
    fun writeCustomConfigMigrationFile(migrateFrom: Path?) {
      val markerFile = getMarkerFilePath()
      if (markerFile.exists()) {
        log.error("Marker file $markerFile shouldn't exist")
      }
      markerFile.write(migrateFrom?.systemIndependentPath ?: "")
    }

    private fun getMarkerFilePath() = Paths.get(PathManager.getConfigPath(), "migrate.config")

  }
}
