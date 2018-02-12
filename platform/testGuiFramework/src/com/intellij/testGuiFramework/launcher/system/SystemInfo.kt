package com.intellij.testGuiFramework.launcher.system

import com.intellij.testGuiFramework.launcher.system.SystemInfo.SystemType.*

/**
 * @author Sergey Karashevich
 */
object SystemInfo {

  enum class SystemType {
    WINDOWS, UNIX, MAC
  }

  fun getSystemType(): SystemType {
    val osName = System.getProperty("os.name").toLowerCase()
    return when {
      osName.contains("win") -> WINDOWS
      osName.contains("mac") -> MAC
      osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> UNIX
      else -> throw Exception("Unknown operation system with name: \"$osName\"")
    }
  }

  fun getExt(): String {
    val sysType = getSystemType()
    return when (sysType) {
      WINDOWS -> "exe"
      MAC -> "sit"
      UNIX -> "tar.gz"
    }
  }

  fun isMac() = (SystemInfo.getSystemType() == SystemInfo.SystemType.MAC)
  fun isWin() = (SystemInfo.getSystemType() == SystemInfo.SystemType.WINDOWS)
  fun isUnix() = (SystemInfo.getSystemType() == SystemInfo.SystemType.UNIX)

}