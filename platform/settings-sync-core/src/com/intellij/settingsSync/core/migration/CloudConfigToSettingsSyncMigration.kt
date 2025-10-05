package com.intellij.settingsSync.core.migration

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.ide.plugins.DisabledPluginsState
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.StringUtil
import com.intellij.settingsSync.*
import com.intellij.settingsSync.core.*
import com.intellij.settingsSync.core.config.EDITOR_FONT_SUBCATEGORY_ID
import com.intellij.util.system.OS
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.*

internal class CloudConfigToSettingsSyncMigration : SettingsSyncMigration {

  override fun getLocalDataIfAvailable(appConfigDir: Path): SettingsSnapshot? {
    return processLocalData(appConfigDir) { path ->
      readLocalData(path)
    }
  }

  override fun isLocalDataAvailable(appConfigDir: Path): Boolean {
    return null != processLocalData(appConfigDir) { it }
  }

  private fun <T> processLocalData(appConfigDir: Path, processor: (Path) -> T?): T? {
    try {
      val jbaConfigPath = appConfigDir / "jba_config"
      val statusInfoFile = jbaConfigPath / STATUS_INFO_FILE
      if (statusInfoFile.exists()) {
        val statusInfo = StatusInfo.valueOf(statusInfoFile.readText())
        if (statusInfo == StatusInfo.JBA_CONNECTED) {
          return processor(jbaConfigPath)
        }
        else {
          LOG.info("Cloud config sync was not active (status is '$statusInfo') => no migration needed")
        }
      }
      else {
        LOG.info("No data from cloudConfig in jba_config folder => no migration needed")
      }
    }
    catch (e: Exception) {
      LOG.error("Could not read data from cloudConfig => no migration ", e)
    }
    return null
  }

  private fun readLocalData(jbaConfigPath: Path): SettingsSnapshot {
    val fileStates = mutableSetOf<FileState>()
    Files.list(jbaConfigPath).forEach { path ->
      val osPrefix = calcOS() + "."
      val isPerOsSetting = path.name.startsWith(osPrefix)

      if (!TOP_LEVEL_SPECIAL_FILES.contains(path.name) && filterOsName(path.name)) {
        if (path.isDirectory()) {
          path.listDirectoryEntries().forEach {
            val relative = jbaConfigPath.relativize(it).invariantSeparatorsPathString
            // mac.keymaps/ -> keymaps/ (this was incorrect to store keymaps per-os)
            val fileSpec = if (isPerOsSetting) relative.removePrefix(osPrefix) else relative
            val content = it.readBytes()
            fileStates.add(FileState.Modified(fileSpec, content))
          }
        }
        else {
          // mac.keymap.xml -> options/mac/keymap.xml
          val fileSpec : String
          if (isPerOsSetting) {
            val plainPath = path.name.removePrefix(osPrefix)
            if (plainPath == "ui.lnf.xml") { // this xml was incorrectly marked as per-os in cloud-config, but it is not
              fileSpec = plainPath
            }
            else {
              fileSpec =  getPerOsSettingsStorageFolderName() + "/" + plainPath
            }
          }
          else {
            fileSpec = path.name
          }
          val content = path.readBytes()
          fileStates.add(FileState.Modified("options/$fileSpec", content))
        }
      }
    }
    return SettingsSnapshot(SettingsSnapshot.MetaInfo(Instant.now(), getLocalApplicationInfo()), fileStates,
                            plugins = null, emptyMap(), emptySet())
  }

  override fun migrateCategoriesSyncStatus(appConfigDir: Path, syncSettings: SettingsSyncSettings) {
    val jbaConfigDir = (appConfigDir / "jba_config")

    val states: MutableMap<String, ConfigState.Type> = HashMap()
    loadStates(LAYOUT_CONFIG_FILENAME, jbaConfigDir, states)
    loadStates(LOCAL_LAYOUT_CONFIG_FILENAME, jbaConfigDir, states)

    val classesToDisable = states.filterValues { type -> type == ConfigState.Type.Disable || type == ConfigState.Type.DisableLocally }.keys
    if (classesToDisable.isNotEmpty()) {
      LOG.info("Disabling sync for following classes: $classesToDisable")
      for (className in classesToDisable) {
        disableCategoryForClass(className, syncSettings)
      }
    }

    syncSettings.setSubcategoryEnabled(SettingsCategory.UI, EDITOR_FONT_SUBCATEGORY_ID, false)
  }

  private fun disableCategoryForClass(className: String, syncSettings: SettingsSyncSettings) {
    try {
      val clazz = Class.forName(className)
      val state = clazz.annotations.find { it is State } as? State
      val category = state?.category
      if (category != null && category != SettingsCategory.OTHER) {
        syncSettings.setCategoryEnabled(category, false)
      }
    }
    catch (e: Exception) {
      LOG.error("Couldn't find class $className", e)
    }
  }

  companion object {
    val LOG = logger<CloudConfigToSettingsSyncMigration>()

    private val CACHES_DIR = "caches"
    private val OS_FILE = "os"
    private val VERSION_FILE = "version"
    private val STATUS_INFO_FILE = "status.info"
    private val AUTO_UPDATE_PLUGINS_FILE = "auto_update_plugins"
    private val LOCAL_CHANGES_FILE = "local.changes"
    private val AUTO_CONNECT_FILE = ".auto_connect"
    private val LOCAL_DISABLED_PLUGINS_FILENAME = "local_" + DisabledPluginsState.DISABLED_PLUGINS_FILENAME
    val LAYOUT_CONFIG_FILENAME = "layout_config.txt"
    val LOCAL_LAYOUT_CONFIG_FILENAME = "local_$LAYOUT_CONFIG_FILENAME"
    private val INSTALLED_PLUGINS_FILENAME: String = "installed_plugins.txt"

    private val TOP_LEVEL_SPECIAL_FILES = listOf(
      CACHES_DIR, AUTO_UPDATE_PLUGINS_FILE, OS_FILE, VERSION_FILE, STATUS_INFO_FILE,
      DisabledPluginsState.DISABLED_PLUGINS_FILENAME, INSTALLED_PLUGINS_FILENAME, LOCAL_DISABLED_PLUGINS_FILENAME,
      LAYOUT_CONFIG_FILENAME, LOCAL_LAYOUT_CONFIG_FILENAME,
      LOCAL_CHANGES_FILE, AUTO_CONNECT_FILE
    )

    val OS_NAMES = arrayOf("mac", "win", "linux", "freebsd", "unix", "unknown")

    fun calcOS(): String = when (OS.CURRENT) {
      OS.Windows -> "win"
      OS.macOS -> "mac"
      OS.Linux -> "linux"
      OS.FreeBSD -> "freebsd"
      else -> "unix"
    }
  }

  fun filterOsName(fileName: String): Boolean {
    if (!fileName.startsWith(calcOS() + ".")) {
      for (name in OS_NAMES) {
        if (fileName.startsWith("$name.")) {
          return false
        }
      }
    }
    return true
  }

  @Throws(IOException::class)
  fun loadStates(fileName: String, configDir: Path, states: MutableMap<String, ConfigState.Type>) {
    loadStates(loadLines(fileName, configDir), states)
  }

  private fun loadStates(lines: List<String>, states: MutableMap<String, ConfigState.Type>) {
    for (line in lines) {
      val id = getPluginId(line).idString
      val type = getRuleValue(line)

      states[id] = ConfigState.Type.valueOf(type)
    }
  }

  private fun getRuleValue(text: String): String {
    val rule = StringUtil.substringAfter(text, ":")!!
    return rule
  }

  private fun getPluginId(text: String): PluginId {
    val pluginId = StringUtil.substringBefore(text, ":")
    LOG.assertTrue(pluginId != null, "Couldn't find id in text '$text'")
    return PluginId.getId(pluginId!!)
  }

  @Throws(IOException::class)
  private fun loadLines(fileName: String, jbaConfigDir: Path): List<String> {
    return loadLines(jbaConfigDir.resolve(fileName))
  }

  @Throws(IOException::class)
  private fun loadLines(file: Path): List<String> {
    return try {
      Files.readAllLines(file)
    }
    catch (e: FileNotFoundException) {
      ArrayList()
    }
    catch (e: NoSuchFileException) {
      ArrayList()
    }
  }



}
