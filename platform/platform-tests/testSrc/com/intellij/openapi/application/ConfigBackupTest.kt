// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.util.io.write
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val CONFIG_PREFIX = "IntelliJIdea2020.3"

class ConfigBackupTest : ConfigImportHelperBaseTest() {
  private lateinit var dirToBackup: Path
  private lateinit var configDir: Path
  private lateinit var backupDir: Path

  @Before fun setup() {
    dirToBackup = newTempDir("temp-settings").apply { resolve("options/config.xml").write("config data") }
    configDir = dirToBackup.resolveSibling(CONFIG_PREFIX)
    backupDir = dirToBackup.resolveSibling("${CONFIG_PREFIX}-backup")
  }

  @Test fun `next backup path`() {
    val now = LocalDateTime.now()
    val date = getDateFormattedForBackupDir(now)
    val dir = memoryFs.fs.getPath("${PathManager.getConfigDir()}-backup").resolve(date)

    val path = ConfigBackup.getNextBackupPath(memoryFs.fs.getPath(PathManager.getConfigDir().toString()), now)
    assertEquals("Next backup path is incorrect", dir, path)
  }

  @Test fun `make simple backup`() {
    moveDirToBackup()

    assertTrue("Backup dir doesn't exist", backupDir.exists())
    val child = backupDir.getSingleChild()
    val backedUpDir = child.getSingleChild()
    assertEquals("Wrong backed up dir", "options", backedUpDir.name)
    val backedUpFile = backedUpDir.getSingleChild()
    assertFile(backedUpFile, "config.xml", "config data")
  }

  @Test fun `migrate previous backup format`() {
    val optionsDir = backupDir.resolve("options").createDirectories()
    optionsDir.resolve("other.xml").createFile().writeText("old content")
    val inspectionsDir = backupDir.resolve("inspections").createDirectories()
    inspectionsDir.resolve("Default.xml").createFile().writeText("old content")

    moveDirToBackup()

    val children = backupDir.listDirectoryEntries().sortedBy { it.name }
    assertEquals("Unexpected number of entries inside $backupDir: $children", 2, children.size)
    assertEquals("Wrong folder name", "1970-01-01-00-00", children[0].name)

    val migratedChildren = children[0].listDirectoryEntries().sortedBy { it.name }
    val migratedInspections = migratedChildren[0]
    assertEquals("Wrong migrated folder name", "inspections", migratedInspections.name)
    val migratedInspectionsFile = migratedInspections.getSingleChild()
    assertFile(migratedInspectionsFile, "Default.xml", "old content")

    val migratedOptions = migratedChildren[1]
    assertEquals("Wrong migrated folder name", "options", migratedOptions.name)
    val migratedOptionsFile = migratedOptions.getSingleChild()
    assertFile(migratedOptionsFile, "other.xml", "old content")

    val backedUpDir = children[1].getSingleChild()
    assertEquals("Wrong backed up dir", "options", backedUpDir.name)
    val backedUpFile = backedUpDir.getSingleChild()
    assertFile(backedUpFile, "config.xml", "config data")
  }

  @Test fun `cleanup backups if there are too many of them`() {
    val now = LocalDateTime.now()
    for (i in 1..ConfigBackup.MAX_BACKUPS_NUMBER) {
      val date = getDateFormattedForBackupDir(now.minusDays(i.toLong()))
      createBackupDirForDate(date)
    }

    moveDirToBackup()

    val children = backupDir.listDirectoryEntries().sortedBy { it.name }
    assertEquals("Unexpected number of entries inside $backupDir: $children", ConfigBackup.MAX_BACKUPS_NUMBER, children.size)
    val oldestDate = getDateFormattedForBackupDir(now.minusDays(ConfigBackup.MAX_BACKUPS_NUMBER.toLong()))
    assertFalse("The oldest dir should have been deleted", children.any { it.name == oldestDate })
  }

  @Test fun `create backup with index if there is already folder with current date`() {
    // during the test this date can become not now, i.e. non-conflicting with the next backup, effectively making the test useless,
    // however, it is ok if the test will be useful
    val now = LocalDateTime.now()
    val date1 = getDateFormattedForBackupDir(now)
    val date2 = getDateFormattedForBackupDir(now.plusMinutes(1))
    val date3 = getDateFormattedForBackupDir(now.plusMinutes(2))
    val dates = listOf(date1, date2, date3)
    dates.forEach {
      createBackupDirForDate(it)
    }

    moveDirToBackup()

    val children = backupDir.listDirectoryEntries().sortedBy { it.name }
    assertEquals("Unexpected number of entries inside $backupDir: $children", 4, children.size)
    val createdDir = children.find { it.name !in dates }!!
    val backedUpDir = createdDir.getSingleChild()
    assertEquals("Wrong backed up dir", "options", backedUpDir.name)
    val backedUpFile = backedUpDir.getSingleChild()
    assertFile(backedUpFile, "config.xml", "config data")
  }

  private fun moveDirToBackup() {
    ConfigBackup(configDir).moveToBackup(dirToBackup)
  }

  private fun createBackupDirForDate(date1: String): Path = backupDir.resolve(date1).createDirectories()

  private fun getDateFormattedForBackupDir(dateTime: LocalDateTime): String =
    dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm"))

  private fun Path.getSingleChild(): Path {
    val children = this.listDirectoryEntries()
    assertEquals("Unexpected number of entries inside $this: $children", 1, children.size)
    return children[0]
  }

  private fun assertFile(actualFile: Path, expectedFileName: String, expectedContent: String) {
    assertEquals("Wrong config file", expectedFileName, actualFile.name)
    assertEquals("Wrong config file content", expectedContent, actualFile.readText())
  }
}
