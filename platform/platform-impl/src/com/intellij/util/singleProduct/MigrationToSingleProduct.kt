// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.singleProduct

import com.intellij.idea.AppMode
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.util.PlatformUtils
import com.intellij.util.Restarter
import com.intellij.util.system.OS
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.io.path.name
import kotlin.system.exitProcess

private const val MIGRATION_FILE_MARKER = "migration_from_community_attempted.txt"

@ApiStatus.Internal
fun migrateCommunityToSingleProductIfNeeded(args: List<String>) {
  if (
    OS.CURRENT != OS.macOS ||
    !(PlatformUtils.isIdeaUltimate() || PlatformUtils.isPyCharmPro()) ||
    AppMode.isRemoteDevHost()
  ) return

  val currentDir = PathManager.getHomeDir().parent
  val currentDirName = currentDir.name
  val newDirName = currentDirName.replace(" CE", "").replace(" Community Edition", "")
  if (newDirName == currentDirName) return

  val newDir = currentDir.resolveSibling(newDirName)
  if (Files.exists(newDir)) return

  // a marker file is used because standard storage is unavailable at this early startup stage
  val migrationAttemptMarker = PathManager.getConfigDir().resolve(MIGRATION_FILE_MARKER)
  try {
    Files.writeString(migrationAttemptMarker, "", StandardOpenOption.CREATE_NEW)
  }
  catch (_: Exception) {
    return
  }

  val renameCommand = listOf("/bin/mv", "-n", currentDir.toString(), newDir.toString())
  val startCommand = listOf(newDir.resolve("Contents/MacOS/${ApplicationNamesInfo.getInstance().scriptName}").toString()) + args
  Restarter.setMainAppArgs(args)  // fallback if the rename fails
  Restarter.scheduleRestart(false, renameCommand, startCommand)
  exitProcess(0)
}
