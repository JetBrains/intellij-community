// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.systemIndependentPath
import com.intellij.util.io.write
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files
import java.nio.file.Path

private val log = logger<CustomConfigMigrationOption>()

/**
 * [A marker file](com.intellij.openapi.application.ConfigImportHelper.CUSTOM_MARKER_FILE_NAME) is created in the config directory
 * if we need to perform some custom migration on the next startup. The format of the file is defined below.
 *
 * - If we need to start with a clean config ("Restore Default Settings" action), the file is empty.
 * - If we need to import config from the given place ("Import Settings" action), the format is `import <path>`.
 * - If the import has already been performed, but the IDE was restarted (because custom vmoptions were added or removed),
 * and we need to restore some values of system properties indicating the first start after importing the config,
 * then the format is `properties <system properties separated by space`, e.g.
 * ```
 * properties intellij.first.ide.session intellij.config.imported.in.current.session
 * ```
 */
sealed class CustomConfigMigrationOption {
  fun writeConfigMarkerFile() {
    val markerFile = getCustomConfigMarkerFilePath(PathManager.getConfigDir())
    if (markerFile.exists()) {
      log.error("Marker file $markerFile shouldn't exist")
    }
    markerFile.write(getStringPresentation())
  }

  abstract fun getStringPresentation(): String

  override fun toString(): String = getStringPresentation()

  object StartWithCleanConfig : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = ""

    override fun toString(): String = "Start with clean config"
  }

  class MigrateFromCustomPlace(val location: Path) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = IMPORT_PREFIX + location.systemIndependentPath
  }

  class SetProperties(val properties: List<String>) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = PROPERTIES_PREFIX + properties.joinToString(separator = " ")
  }

  companion object {
    private const val IMPORT_PREFIX = "import "
    private const val PROPERTIES_PREFIX = "properties "

    @JvmStatic
    fun readCustomConfigMigrationOptionAndRemoveMarkerFile(configDir: Path): CustomConfigMigrationOption? {
      val markerFile = getCustomConfigMarkerFilePath(configDir)
      if (!markerFile.exists()) return null

      try {
        val lines = Files.readAllLines(markerFile)
        if (lines.isEmpty()) return StartWithCleanConfig
        val line = lines.first()
        when {
          line.isEmpty() -> return StartWithCleanConfig

          line.startsWith(IMPORT_PREFIX) -> {
            val path = markerFile.fileSystem.getPath(line.removePrefix(IMPORT_PREFIX))
            if (!path.exists()) {
              log.warn("$markerFile points to non-existent config: [$lines]")
              return null
            }
            return MigrateFromCustomPlace(path)
          }

          line.startsWith(PROPERTIES_PREFIX) -> {
            val properties = line.removePrefix(PROPERTIES_PREFIX).split(' ')
            return SetProperties(properties)
          }

          else -> {
            log.error("Invalid format of $markerFile: $lines")
            return null
          }
        }
      }
      catch (e: Exception) {
        log.warn("Couldn't load content of $markerFile")
        return null
      }
      finally {
        removeMarkerFile(markerFile)
      }
    }

    private fun removeMarkerFile(markerFile: Path) {
      try {
        markerFile.delete()
      }
      catch (e: Exception) {
        log.warn("Couldn't delete the custom config migration file $markerFile", e)
      }
    }

    @VisibleForTesting
    fun getCustomConfigMarkerFilePath(configDir: Path): Path {
      return configDir.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME)
    }
  }
}
