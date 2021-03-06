// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.delete
import com.intellij.util.io.exists
import com.intellij.util.io.isDirectory
import org.jetbrains.annotations.ApiStatus
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import kotlin.io.path.createDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.name

@ApiStatus.Internal
class ConfigBackup(private val configDir: Path) {

  @Throws(IOException::class)
  fun moveToBackup(dirToMove: File) {
    val backupsDir = getBackupsDir(configDir)
    if (backupsDir.exists()) {
      migratePreviousBackupIfExists(backupsDir)
      cleanupOldBackups(backupsDir)
    }

    val backupPath = getNextBackupPath(configDir)
    LOG.info("Move backup from $dirToMove to $backupPath")
    FileUtil.copyDir(dirToMove, backupPath.toFile())
    FileUtil.delete(dirToMove)
  }

  private fun migratePreviousBackupIfExists(backupsDir: Path) {
    if (ConfigImportHelper.isConfigDirectory(backupsDir)) {
      try {
        val oldBackup = backupsDir.resolve("1970-01-01-00-00").createDirectory()
        for (file in backupsDir.listDirectoryEntries()) {
          if (!file.isDirectory() || !file.name.looksLikeDate()) {
            FileUtil.copyDir(file.toFile(), oldBackup.resolve(file.name).toFile())
            FileUtil.delete(file.toFile())
          }
        }
      }
      catch (e: Exception) {
        // migration of the previous backup is less important task than creating a backup => handling the exception here
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

  private fun cleanupOldBackups(backupsDir: Path) {
    val children = backupsDir.listDirectoryEntries().sortedBy { it.name }
    if (children.size >= MAX_BACKUPS_NUMBER) {
      for (i in 0..children.size - MAX_BACKUPS_NUMBER) {
        val oldDir = children[i]
        try {
          oldDir.delete(recursively = true)
        }
        catch (e: Exception) {
          // cleanup of previous backups is less important task than creating a backup => handling the exception here
          LOG.warn(e)
        }
      }
    }
  }

  companion object {
    const val MAX_BACKUPS_NUMBER = 10
    private val LOG = logger<ConfigBackup>()
    private const val DATE_FORMAT = "yyyy-MM-dd-HH-mm"

    fun getNextBackupPath(configDir: Path): Path {
      val now = LocalDateTime.now()
      val format = DateTimeFormatter.ofPattern(DATE_FORMAT)
      val date = now.format(format)

      val dir = getBackupsDir(configDir).resolve(date)
      if (!dir.exists()) {
        return dir
      }

      LOG.info("$dir already exists")
      val id = UUID.randomUUID().toString()
      val dirWithIndex = getBackupsDir(configDir).resolve("$date-$id")
      if (dirWithIndex.exists()) {
        LOG.warn("Even $dirWithIndex already exists")
      }
      return dirWithIndex
    }

    private fun getBackupsDir(configDir: Path): Path {
      return configDir.resolveSibling(configDir.name + "-backup")
    }
  }
}
