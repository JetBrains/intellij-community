// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

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
@ApiStatus.Internal
sealed class CustomConfigMigrationOption {
  @JvmOverloads
  @Throws(IOException::class)
  fun writeConfigMarkerFile(configDir: Path = PathManager.getOriginalConfigDir()) {
    val markerFile = getCustomConfigMarkerFilePath(configDir)
    if (Files.exists(markerFile)) {
      log.error("Marker file $markerFile shouldn't exist")
    }
    NioFiles.createDirectories(markerFile.parent)
    Files.writeString(markerFile, getStringPresentation(), Charsets.UTF_8)
  }

  abstract fun getStringPresentation(): String

  override fun toString(): String = getStringPresentation()

  object StartWithCleanConfig : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = ""
    override fun toString(): String = "Start with clean config"
  }

  class MigrateFromCustomPlace(val location: Path) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = IMPORT_PREFIX + location.toString().replace(File.separatorChar, '/')
  }

  /**
   * A variant of [MigrateFromCustomPlace] which migrates plugins only. 
   * This option is supposed to be used only to migrate plugins from a regular IDE to its frontend process. 
   * It'll be removed when the frontend process starts loading plugins from the same directory as a regular IDE (RDCT-1738).  
   */
  class MigratePluginsFromCustomPlace(val configLocation: Path) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = MIGRATE_PLUGINS_PREFIX + configLocation.toString().replace(File.separatorChar, '/')
  }

  class SetProperties(val properties: List<String>) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = PROPERTIES_PREFIX + properties.joinToString(separator = " ")
  }

  object MergeConfigs : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = MERGE_CONFIGS_COMMAND
  }

  companion object {
    private const val IMPORT_PREFIX = "import "
    private const val MIGRATE_PLUGINS_PREFIX = "migrate-plugins "
    private const val PROPERTIES_PREFIX = "properties "
    private const val MERGE_CONFIGS_COMMAND = "merge-configs"

    @JvmStatic
    fun readCustomConfigMigrationOptionAndRemoveMarkerFile(configDir: Path): CustomConfigMigrationOption? {
      val markerFile = getCustomConfigMarkerFilePath(configDir)
      if (!Files.exists(markerFile)) return null

      try {
        val lines = Files.readAllLines(markerFile)
        if (lines.isEmpty()) return StartWithCleanConfig

        val line = lines.first()
        when {
          line.isEmpty() -> return StartWithCleanConfig

          line.startsWith(IMPORT_PREFIX) -> {
            val path = markerFile.fileSystem.getPath(line.removePrefix(IMPORT_PREFIX))
            if (!Files.exists(path)) {
              log.warn("$markerFile points to non-existent config: [$lines]")
              return null
            }
            return MigrateFromCustomPlace(path)
          }
          
          line.startsWith(MIGRATE_PLUGINS_PREFIX) -> {
            return MigratePluginsFromCustomPlace(markerFile.fileSystem.getPath(line.removePrefix(MIGRATE_PLUGINS_PREFIX)))
          }

          line.startsWith(PROPERTIES_PREFIX) -> {
            val properties = line.removePrefix(PROPERTIES_PREFIX).split(' ')
            return SetProperties(properties)
          }

          line == MERGE_CONFIGS_COMMAND -> return MergeConfigs

          else -> {
            log.error("Invalid format of $markerFile: $lines")
            return null
          }
        }
      }
      catch (_: Exception) {
        log.warn("Couldn't load content of $markerFile")
        return null
      }
      finally {
        removeMarkerFile(markerFile)
      }
    }

    private fun removeMarkerFile(markerFile: Path) {
      try {
        Files.delete(markerFile)
      }
      catch (e: Exception) {
        log.warn("Couldn't delete the custom config migration file $markerFile", e)
      }
    }

    @VisibleForTesting
    fun getCustomConfigMarkerFilePath(configDir: Path): Path = configDir.resolve(ConfigImportHelper.CUSTOM_MARKER_FILE_NAME)
    
    fun doesCustomConfigMarkerExist(configDir: Path): Boolean = getCustomConfigMarkerFilePath(configDir).exists()
  }
}
