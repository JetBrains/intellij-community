package com.intellij.remoteDev

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import com.intellij.util.system.CpuArch
import com.sun.jna.platform.win32.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

class OsRegistryConfigProvider(private val configName: String) {
  companion object {
    private val logger = logger<OsRegistryConfigProvider>()
  }

  init {
    require(!configName.contains(' ')) { "'$configName' should not contains spaces" }
  }

  class OsRegistrySystemSetting<T>(val value: T, val osOriginLocation: String?) {
    val isSetFromOs = osOriginLocation != null
  }

  fun get(key: String): OsRegistrySystemSetting<String>? {
    val systemValue = when {
      SystemInfo.isWindows -> getFromRegistry(key)
      SystemInfo.isLinux -> getFromXdgConfig(key)
      SystemInfo.isMac -> getFromLibraryApplicationSupport(key)
      else -> error("Unknown OS")
    }

    if (systemValue != null) {
      logger.info("OS provided value for $key=${systemValue.value} in $configName config, origin=${systemValue.osOriginLocation}")
    }
    return systemValue
  }

  // key: SOFTWARE\JetBrains\$configName, value: regValue, value type: REG_SZ
  // prio: HKLM64 -> 32 -> HKCU64 -> 32
  private fun getFromRegistry(regValue: String): OsRegistrySystemSetting<String>? {
    val regKey = "SOFTWARE\\JetBrains\\$configName"

    logger.debug("Looking for $regValue in registry $regKey, is32Bit=${CpuArch.isIntel32()}")

    // these WOW64 keys are ignored by 32-bit Windows, see https://docs.microsoft.com/en-us/windows/win32/sysinfo/registry-key-security-and-access-rights
    val uris = listOf(
      "HKLM_64" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, regKey, regValue, WinNT.KEY_WOW64_64KEY) },
      "HKLM_32" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, regKey, regValue, WinNT.KEY_WOW64_32KEY) },

      "HKCU_64" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, regKey, regValue, WinNT.KEY_WOW64_64KEY) },
      "HKCU_32" to { Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, regKey, regValue, WinNT.KEY_WOW64_32KEY) }
    )

    fun Pair<String, () -> String>.readable() = "${this.first}\\$regKey\\$regValue"

    return uris.mapNotNull {
      val uri = try {
        logger.debug("Searching for ${it.readable()} in registry")
        it.second()
      }
      catch (t: Throwable) {
        when (t) {
          is Win32Exception -> {
            if (t.errorCode == WinError.ERROR_FILE_NOT_FOUND) logger.debug("registry entry not found at ${it.readable()}")
            else logger.warn("Failed to get registry entry at ${it.readable()}, HRESULT=${t.hr.toInt()}", t)
          }
          else -> {
            logger.warn("Failed to get registry entry at ${it.readable()}", t)
          }
        }
        null
      }

      if (uri != null) {
        logger.info("Found registry entry at ${it.readable()}, value=$uri")
        OsRegistrySystemSetting(uri, it.readable())
      }
      else {
        null
      }
    }.firstOrNull()
  }

  // https://specifications.freedesktop.org/basedir-spec/basedir-spec-0.6.html
  private fun getFromXdgConfig(key: String): OsRegistrySystemSetting<String>? {

    val configPath = "JetBrains/$configName/config.json"
    val env = EnvironmentUtil.getEnvironmentMap()
    val home = System.getProperty("user.home")

    // non-user writable location first, even if that's wrong according to spec as that's what we want
    val configLookupDirs = listOf("/etc/xdg/", env["XDG_CONFIG_HOME"] ?: "$home/.config/").toMutableList()
    logger.info("Looking for $key in xdg config dirs: $configLookupDirs")

    // we already set /etc/xdg as the first one, so no need to default to it
    val xdgConfigDirs = (env["XDG_CONFIG_DIRS"] ?: "").split(":").filter { it.isNotEmpty() }
    configLookupDirs.addAll(xdgConfigDirs)

    return getFromDirectories(key, configLookupDirs.map { File(it) }, configPath)
  }

  private fun getFromDirectories(key: String, dirs: List<File>, configPath: String): OsRegistrySystemSetting<String>? =
    dirs.mapNotNull {
      val file = File(it, configPath)
      getFromJsonFile(key, file)
    }.firstOrNull()

  private fun getFromJsonFile(key: String, file: File): OsRegistrySystemSetting<String>? {
    logger.debug("Trying to get $key from file=${file.canonicalPath}")

    if (!file.exists()) {
      logger.debug("File=${file.canonicalPath} does not exist.")
      return null
    }
    return try {
      val root = Json.Default.parseToJsonElement(file.readText()) as? JsonObject
      val uri = (root?.get(key) as? JsonPrimitive)?.content
      if (uri != null) {
        logger.info("Found $key in file=${file.canonicalPath}, value=$uri")
        OsRegistrySystemSetting(uri, file.canonicalPath)
      }
      else null
    }
    catch (t: Throwable) {
      logger.warn("Failed to read json for $key at location $file", t)
      null
    }
  }

  private fun getFromLibraryApplicationSupport(key: String): OsRegistrySystemSetting<String>? {
    val home = System.getProperty("user.home")
    val dirs = listOf("/", "$home/")
    val configPath = "Library/Application Support/JetBrains/$configName/config.json"
    return getFromDirectories(key, dirs.map { File(it) }, configPath)
  }
}