// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.EnvironmentUtil
import com.intellij.util.system.CpuArch
import com.sun.jna.platform.win32.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.io.File
import kotlin.Pair

// todo stateflow
@Internal
interface OsRegistryConfig {
  companion object {
    fun getInstance(configName: String): OsRegistryConfig = OsRegistryConfigProvider(configName)
  }

  fun isTrue(key: String, defaultValue: Boolean): Boolean
  fun isTrue(key: String): Boolean?
  fun get(key: String): OsRegistrySystemSetting<String>?
}

class OsRegistrySystemSetting<T> internal constructor(val value: T, val osOriginLocation: String?)

private class OsRegistryConfigProvider(private val configName: String) : OsRegistryConfig {
  companion object {
    private val logger = logger<OsRegistryConfigProvider>()
    private const val configXmlFilename = "config.xml"
    private const val etcXdgPath = "/etc/xdg/"
    private fun getConfigSubDirectoryPath(configName: String) = "JetBrains/$configName"
  }

  init {
    require(!configName.contains(' ')) { "'$configName' should not contain spaces" }
  }

  private val keyRegex = Regex("^\\w+$")

  override fun isTrue(key: String): Boolean? {
    val osSetting = get(key)

    return osSetting?.value?.lowercase()?.toBooleanStrictOrNull()
  }

  /**
   * Gets value for [key] from system storage and tries to convert it into boolean
   * "true" string (with any casing) will be interpreted as true
   * "false" string (with any casing) will be interpreted as false
   * defaultValue otherwise
   */
  override fun isTrue(key: String, defaultValue: Boolean): Boolean {
    val osSetting = get(key)
    if (osSetting == null) return defaultValue

    val result = osSetting.value.lowercase().toBooleanStrictOrNull()
    if (result != null) return result

    logger.warn("Unknown value '${osSetting.value}' from ${osSetting.osOriginLocation} (only 'true' or 'false' are recognized). Assume '$defaultValue'")
    return defaultValue
  }

  override fun get(key: String): OsRegistrySystemSetting<String>? {
    require(key.matches(keyRegex)) { "Key '$key' does not match regex '${keyRegex.pattern}'" }

    val systemValue = when {
      SystemInfo.isWindows -> getFromRegistry(key)
      SystemInfo.isLinux -> getFromXdgConfig(key)
      SystemInfo.isMac -> getFromLibraryApplicationSupport(key)
      else -> error("Unknown OS")
    }

    if (systemValue != null) {
      logger.info("OS provided value for '$key' is '${systemValue.value}' in config '$configName', origin=${systemValue.osOriginLocation}")
    } else {
      logger.info("OS provided value for '$key' is not found")
    }
    return systemValue
  }

  // key: SOFTWARE\JetBrains\$configName, value: regValue, value type: REG_SZ
  // prio: HKLM64 -> 32 -> HKCU64 -> 32
  private fun getFromRegistry(regValue: String): OsRegistrySystemSetting<String>? {
    val regKey = "SOFTWARE\\JetBrains\\$configName"

    logger.debug("Looking for '$regValue' in registry '$regKey', is32Bit=${CpuArch.isIntel32()}")

    // these WOW64 keys are ignored by 32-bit Windows, see https://docs.microsoft.com/en-us/windows/win32/sysinfo/registry-key-security-and-access-rights
    val uris = listOf(
      "HKLM_64" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, regKey, regValue, WinNT.KEY_WOW64_64KEY) },
      "HKLM_32" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, regKey, regValue, WinNT.KEY_WOW64_32KEY) },

      "HKCU_64" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, regKey, regValue, WinNT.KEY_WOW64_64KEY) },
      "HKCU_32" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, regKey, regValue, WinNT.KEY_WOW64_32KEY) }
    )

    fun Pair<String, () -> String>.readable() = "${this.first}\\$regKey\\$regValue"

    logger.info("Looking for '$regValue' value in ${uris.map { it.readable() }}")

    return uris.mapNotNull {
      val uri = try {
        logger.debug("Searching for '${it.readable()}' in registry")
        it.second()
      }
      catch (t: Throwable) {
        when (t) {
          is Win32Exception -> {
            if (t.errorCode == WinError.ERROR_FILE_NOT_FOUND) logger.debug("registry entry not found at ${it.readable()}")
            else logger.warn("Failed to get registry entry at '${it.readable()}', HRESULT=${t.hr.toInt()}", t)
          }
          else -> {
            logger.warn("Failed to get registry entry at '${it.readable()}'", t)
          }
        }
        null
      }

      if (uri != null) {
        logger.info("Found registry entry at '${it.readable()}', value='$uri'")
        OsRegistrySystemSetting(uri, it.readable())
      }
      else {
        null
      }
    }.firstOrNull()
  }

  // https://specifications.freedesktop.org/basedir-spec/basedir-spec-0.6.html
  private fun getFromXdgConfig(key: String): OsRegistrySystemSetting<String>? {

    val configDirectoryPath = getConfigSubDirectoryPath(configName)
    val env = EnvironmentUtil.getEnvironmentMap()
    val home = System.getProperty("user.home")

    // non-user writable location first, even if that's wrong according to spec as that's what we want
    val configLookupDirs = listOf(etcXdgPath, env["XDG_CONFIG_HOME"] ?: "$home/.config/").toMutableList()

    // we already set /etc/xdg as the first one, so no need to default to it
    val xdgConfigDirs = (env["XDG_CONFIG_DIRS"] ?: "").split(":").filter { it.isNotEmpty() }
    configLookupDirs.addAll(xdgConfigDirs)

    logger.debug("Looking for $key in xdg config dirs: $configLookupDirs")

    return getFromDirectories(key, configLookupDirs.map { File(it) }, configDirectoryPath)
  }

  private fun getFromDirectories(key: String, dirs: List<File>, configDirectoryPath: String): OsRegistrySystemSetting<String>? {
    val allPossibleLocations = dirs.map { it.resolve(configDirectoryPath) }.flatMap { listOf(it.resolve(key), it.resolve(configXmlFilename)) }
    logger.info("Looking for '$key' value in $allPossibleLocations")

    for (dir in dirs) {
      // get from file with name $key
      val valueFromKeyFile = getFromKeyFile(dir, configDirectoryPath, key)
      if (valueFromKeyFile != null)
        return valueFromKeyFile

      // fallback to config.json
      val configJsonFile = File(File(dir, configDirectoryPath), configXmlFilename)
      val valueFromJsonFile = parseFile(key, configJsonFile)
      if (valueFromJsonFile != null)
        return valueFromJsonFile
    }

    return null
  }

  private fun getFromKeyFile(it: File, configDirectoryPath: String, key: String): OsRegistrySystemSetting<String>? {
    val keyFile = File(File(it, configDirectoryPath), key)
    logger.debug("Trying to get '$key' from file '${keyFile.canonicalPath}'")
    if (!keyFile.exists()) {
      logger.debug("File '${keyFile.canonicalPath}' does not exist.")
      return null
    }
    try {
      // todo: think about it: should we trim the value?
      val keyFileContents = keyFile.readText().trim()
      logger.info("Found '${keyFile.canonicalPath}' from file '${keyFile.canonicalPath}'. value='$keyFileContents'")
      return OsRegistrySystemSetting(keyFileContents, keyFile.canonicalPath)
    } catch (e: Throwable) {
      logger.warn("Failed to read setting '$key' from '${keyFile.canonicalPath}'", e)
      return null
    }
  }

  // Currently it's XML implementation, can be also JSON
  private fun parseFile(key: String, file: File): OsRegistrySystemSetting<String>? {
    logger.debug("Trying to get key '$key' from file '${file.canonicalPath}'")

    if (!file.exists()) {
      logger.debug("File '${file.canonicalPath}' does not exist.")
      return null
    }

    return try {
      val root = JDOMUtil.load(file)
      val resultValue = root.getChild(key).value
      if (resultValue != null) {
        logger.info("Found '$key' in file '${file.canonicalPath}'. Value='$resultValue'")
        OsRegistrySystemSetting(resultValue, file.canonicalPath)
      }
      else null
    }
    catch (t: Throwable) {
      logger.warn("Failed to read json for '$key' at location '$file'", t)
      null
    }
  }

  // todo?: PathManager.getCommonDataPath()
  private fun getFromLibraryApplicationSupport(key: String): OsRegistrySystemSetting<String>? {
    val home = System.getProperty("user.home")
    val dirs = listOf("/", "$home/")
    val configDirectoryPath = "Library/Application Support/JetBrains/$configName"
    return getFromDirectories(key, dirs.map { File(it) }, configDirectoryPath)
  }
}