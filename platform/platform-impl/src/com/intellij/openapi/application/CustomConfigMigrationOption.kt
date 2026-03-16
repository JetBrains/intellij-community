// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * [A marker file](com.intellij.openapi.application.ConfigImportHelper.CUSTOM_MARKER_FILE_NAME) is created in the config directory
 * if we need to perform some custom migration on the next startup. The format of the file is defined below.
 *
 * - If we need to start with a clean config ("Restore Default Settings" action), the file is empty.
 * - If we need to import config from the given place ("Import Settings" action), the format is `import <path>`.
 * - If we need to import config from a previous version the same as it happens if the config directory is absent, the format is `merge-configs`.
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
    override fun getStringPresentation(): String = IMPORT_PREFIX + location.toString().replace('\\', '/')
  }

  /**
   * A variant of [MigrateFromCustomPlace] which migrates plugins only. 
   * This option is supposed to be used only to migrate plugins from a regular IDE to its frontend process. 
   * It'll be removed when the frontend process starts loading plugins from the same directory as a regular IDE (RDCT-1738).  
   */
  class MigratePluginsFromCustomPlace(val configLocation: Path) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = MIGRATE_PLUGINS_PREFIX + configLocation.toString().replace('\\', '/')
  }

  class SetProperties(val properties: List<Pair<String, String>>) : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = SET_PROPERTIES_PREFIX + properties.joinToString(separator = ";") { (k, v) -> "$k $v" }
  }

  object MergeConfigs : CustomConfigMigrationOption() {
    override fun getStringPresentation(): String = MERGE_CONFIGS_COMMAND
  }

  companion object {
    private val log = logger<CustomConfigMigrationOption>()

    private const val IMPORT_PREFIX = "import "
    private const val MIGRATE_PLUGINS_PREFIX = "migrate-plugins "
    private const val SET_PROPERTIES_PREFIX = "set-properties "
    private const val MERGE_CONFIGS_COMMAND = "merge-configs"

    @JvmStatic
    fun readCustomConfigMigrationOptionAndRemoveMarkerFile(configDir: Path): CustomConfigMigrationOption? {
      val markerFile = getCustomConfigMarkerFilePath(configDir)

      try {
        val lines = Files.readAllLines(markerFile, Charsets.UTF_8)
        val line = lines.firstOrNull()
        return when {
          line.isNullOrEmpty() -> StartWithCleanConfig

          line.startsWith(IMPORT_PREFIX) -> {
            val path = markerFile.fileSystem.getPath(line.removePrefix(IMPORT_PREFIX))
            if (Files.exists(path)) {
              MigrateFromCustomPlace(path)
            }
            else {
              log.warn("$markerFile points to non-existent config: [$lines]")
              null
            }
          }

          line.startsWith(MIGRATE_PLUGINS_PREFIX) -> {
            MigratePluginsFromCustomPlace(markerFile.fileSystem.getPath(line.removePrefix(MIGRATE_PLUGINS_PREFIX)))
          }

          // legacy SetProperties parsing
          line.startsWith("properties ") -> {
            SetProperties(line.removePrefix("properties ").split(' ').map { it to "true" })
          }

          line.startsWith(SET_PROPERTIES_PREFIX) -> {
            SetProperties(line.removePrefix(SET_PROPERTIES_PREFIX).split(';').mapNotNull {
              val list = it.split(' ', limit = 2)
              if (list.size < 2) null else list[0] to list[1]
            })
          }

          line == MERGE_CONFIGS_COMMAND -> MergeConfigs

          else -> {
            log.error("Invalid format of $markerFile: $lines")
            null
          }
        }
      }
      catch (_: NoSuchFileException) {
        return null
      }
      catch (_: Exception) {
        log.warn("Couldn't load content of $markerFile")
        return null
      }
      finally {
        try {
          Files.deleteIfExists(markerFile)
        }
        catch (e: Exception) {
          log.warn("Couldn't delete the custom config migration file $markerFile", e)
        }
      }
    }

    @VisibleForTesting
    fun getCustomConfigMarkerFilePath(configDir: Path): Path = configDir.resolve(InitialConfigImportState.CUSTOM_MARKER_FILE_NAME)
    
    fun doesCustomConfigMarkerExist(configDir: Path): Boolean = Files.exists(getCustomConfigMarkerFilePath(configDir))
  }
}
