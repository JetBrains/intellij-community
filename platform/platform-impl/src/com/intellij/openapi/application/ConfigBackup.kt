// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.delete
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.io.path.*

@ApiStatus.Internal
class ConfigBackup(private val configDir: Path) {

  @Throws(IOException::class)
  fun moveToBackup(dirToMove: Path) {
    val backupDir = getBackupDir(configDir)
    if (backupDir.exists()) {
      migratePreviousBackupIfExists(backupDir)
      cleanupOldBackups(backupDir)
    }

    val backupPath = getNextBackupPath(configDir)
    LOG.info("Move backup from $dirToMove to $backupPath")
    FileUtil.copyDir(dirToMove.toFile(), backupPath.toFile())
    NioFiles.deleteRecursively(dirToMove)
  }

  private fun migratePreviousBackupIfExists(backupDir: Path) {
    if (ConfigImportHelper.isConfigDirectory(backupDir)) {
      try {
        val oldBackup = backupDir.resolve("1970-01-01-00-00").createDirectory()
        for (file in backupDir.listDirectoryEntries()) {
          if (!file.isDirectory() || !file.name.looksLikeDate()) {
            FileUtil.copyDir(file.toFile(), oldBackup.resolve(file.name).toFile())
            NioFiles.deleteRecursively(file)
          }
        }
      }
      catch (e: Exception) {
        // migration of the previous backup is a less important task than creating a backup => handling the exception here
        LOG.warn(e)
      }
    }
  }

  private fun String.looksLikeDate(): Boolean {
    try {
      val format = DateTimeFormatter.ofPattern(DATE_FORMAT)
      format.parse(this)
      return true
    }
    catch (e: DateTimeParseException) {
      return false
    }
  }

  private fun cleanupOldBackups(backupDir: Path) {
    val children = backupDir.listDirectoryEntries().sortedBy { it.name }
    if (children.size >= MAX_BACKUPS_NUMBER) {
      for (i in 0..children.size - MAX_BACKUPS_NUMBER) {
        val oldDir = children[i]
        try {
          oldDir.delete(recursively = true)
        }
        catch (e: Exception) {
          // cleanup of previous backups is a less important task than creating a backup => handling the exception here
          LOG.warn(e)
        }
      }
    }
  }

  companion object {
    const val MAX_BACKUPS_NUMBER: Int = 10
    private val LOG = logger<ConfigBackup>()
    private const val DATE_FORMAT = "yyyy-MM-dd-HH-mm"

    fun getNextBackupPath(configDir: Path, currentDate: LocalDateTime = LocalDateTime.now()): Path {
      val format = DateTimeFormatter.ofPattern(DATE_FORMAT)
      val date = currentDate.format(format)

      val dir = getBackupDir(configDir).resolve(date)
      if (!dir.exists()) {
        return dir
      }

      LOG.info("$dir already exists")
      val id = UUID.randomUUID().toString()
      val dirWithIndex = getBackupDir(configDir).resolve("$date-$id")
      if (dirWithIndex.exists()) {
        LOG.warn("Even $dirWithIndex already exists")
      }
      return dirWithIndex
    }

    private fun getBackupDir(configDir: Path): Path = configDir.resolveSibling(configDir.name + "-backup")
  }
}
