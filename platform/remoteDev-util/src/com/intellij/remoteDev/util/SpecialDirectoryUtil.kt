package com.intellij.remoteDev.util

import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div

@ApiStatus.Experimental
fun getJetBrainsSystemCachesDir(): Path {
  if (SystemInfo.isWindows)
    return Paths.get(System.getenv("LOCALAPPDATA")) / "JetBrains"

  if (SystemInfo.isMac)
    return Paths.get(System.getenv("HOME")) / "Library" / "Caches" / "JetBrains"

  val homeDir = System.getenv("XDG_CACHE_HOME")
  val homeDirFile = if (homeDir.isNullOrEmpty())
    Paths.get(System.getenv("HOME")) / ".cache"
  else
    Paths.get(homeDir)

  return homeDirFile / "JetBrains"
}

@ApiStatus.Experimental
fun getJetBrainsConfigCachesDir(): Path {
  if (SystemInfo.isWindows)
    return Paths.get(System.getenv("APPDATA")) / "JetBrains"

  if (SystemInfo.isMac)
    return Paths.get(System.getenv("HOME")) / "Library" / "Application Support" / "JetBrains"

  val homeDir = System.getenv("XDG_CONFIG_HOME")
  val homeDirFile = if (homeDir.isNullOrEmpty())
    Paths.get(System.getenv("HOME")) / ".config"
  else
    Paths.get(homeDir)

  return homeDirFile / "JetBrains"
}

@ApiStatus.Experimental
fun getJetBrainsSpecialLogsDir(): Path? {
  if (SystemInfo.isMac)
    return Paths.get(System.getenv("HOME")) / "Library" / "Logs" / "JetBrains"

  return null
}