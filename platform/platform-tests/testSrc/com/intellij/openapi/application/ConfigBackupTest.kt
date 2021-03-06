// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.application.ConfigBackup.Companion.MAX_BACKUPS_NUMBER
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.*

private const val CONFIG_PREFIX = "IntelliJIdea2020.3"

class ConfigBackupTest : ConfigImportHelperBaseTest() {

  private lateinit var dirToBackup: File
  private lateinit var configDir: Path
  private lateinit var backupDir: Path

  @Before
  fun setup() {
    dirToBackup = createConfigDirToBackup()
    configDir = localTempDir.rootPath.resolve(CONFIG_PREFIX)
    backupDir = configDir.resolveSibling("$CONFIG_PREFIX-backup")
  }

  @Test
  fun `next backup path`() {
    val date = getDateFormattedForBackupDir(LocalDateTime.now())
    val dir = memoryFs.fs.getPath("${PathManager.getConfigPath()}-backup").resolve(date)

    val path = ConfigBackup.getNextBackupPath(memoryFs.fs.getPath(PathManager.getConfigPath()))
    assertEquals("Next backup path is incorrect", dir, path)
  }

  @Test
  fun `make simple backup`() {
    moveDirToBackup()

    assertTrue("Backup dir doesn't exist", backupDir.exists())
    val child = backupDir.getSingleChild()
    val backedupDir = child.getSingleChild()
    assertEquals("Wrong backed up dir", "options", backedupDir.name)
    val backedupFile = backedupDir.getSingleChild()
    assertFile(backedupFile, "config.xml", "config data")
  }

  @Test
  fun `migrate previous backup format`() {
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

    val backedupDir = children[1].getSingleChild()
    assertEquals("Wrong backed up dir", "options", backedupDir.name)
    val backedupFile = backedupDir.getSingleChild()
    assertFile(backedupFile, "config.xml", "config data")
  }

  @Test
  fun `cleanup backups if there are too many of them`() {
    val now = LocalDateTime.now()
    for (i in 1..MAX_BACKUPS_NUMBER) {
      val date = getDateFormattedForBackupDir(now.minusDays(i.toLong()))
      createBackupDirForDate(date)
    }

    moveDirToBackup()

    val children = backupDir.listDirectoryEntries().sortedBy { it.name }
    assertEquals("Unexpected number of entries inside $backupDir: $children", MAX_BACKUPS_NUMBER, children.size)
    val oldestDate = getDateFormattedForBackupDir(now.minusDays(MAX_BACKUPS_NUMBER.toLong()))
    assertFalse("The oldest dir should have been deleted", children.any { it.name == oldestDate })
  }

  @Test
  fun `create backup with index if there is already folder with current date`() {
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
    val backedupDir = createdDir.getSingleChild()
    assertEquals("Wrong backed up dir", "options", backedupDir.name)
    val backedupFile = backedupDir.getSingleChild()
    assertFile(backedupFile, "config.xml", "config data")
  }

  private fun moveDirToBackup() {
    ConfigBackup(configDir).moveToBackup(dirToBackup)
  }

  private fun createBackupDirForDate(date1: String): Path {
    return backupDir.resolve(date1).createDirectories()
  }

  private fun createConfigDirToBackup(): File {
    val configDir = localTempDir.newDirectory("temp-settings")
    val optionsDir = configDir.resolve("options")
    assertTrue("Couldn't create $optionsDir", optionsDir.mkdir())
    val configFile = optionsDir.resolve("config.xml")
    configFile.writeText("config data")
    return configDir
  }

  private fun getDateFormattedForBackupDir(dateTime: LocalDateTime): String {
    val format = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm")
    return dateTime.format(format)
  }

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