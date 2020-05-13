// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CustomConfigMigrationOptionTest : BareTestFixtureTestCase() {
  @JvmField @Rule val memoryFs = InMemoryFsRule(SystemInfo.isWindows)
  @JvmField @Rule val localTempDir = TempDirectory()

  @Test
  fun `empty marker file indicates clean config`() {
    val configDir = createMarkerFile("")

    val option = readOption(configDir)
    assertTrue("Option parsed incorrectly", option is CustomConfigMigrationOption.StartWithCleanConfig)
  }

  @Test
  fun `marker file with import path`() {
    val path = "/Users/me/Library/Application Support/JetBrains/IntelliJIdea2019.3"
    Files.createDirectories(memoryFs.fs.getPath(path))
    val configDir = createMarkerFile("import $path")

    val option = readOption(configDir)
    assertTrue("Option parsed incorrectly", option is CustomConfigMigrationOption.MigrateFromCustomPlace)
    assertEquals("Import path parsed incorrectly", path, (option as CustomConfigMigrationOption.MigrateFromCustomPlace).location.toString())
  }

  @Test
  fun `marker file with properties to set`() {
    val properties = listOf("intellij.first.ide.session", "intellij.config.imported.in.current.session")
    val configDir = createMarkerFile("properties ${properties.joinToString(" ")}")

    val option = readOption(configDir)
    assertTrue("Option parsed incorrectly", option is CustomConfigMigrationOption.SetProperties)
    assertEquals("Properties parsed incorrectly", properties, (option as CustomConfigMigrationOption.SetProperties).properties)
  }

  private fun readOption(configDir: Path) = CustomConfigMigrationOption.readCustomConfigMigrationOptionAndRemoveMarkerFile(configDir)

  private fun createMarkerFile(content: String): Path {
    val configDir = createConfigDir()
    val markerFile = CustomConfigMigrationOption.getCustomConfigMarkerFilePath(configDir)
    Files.write(markerFile, content.toByteArray())
    return configDir
  }

  private fun createConfigDir(): Path {
    val configPath = PathManager.getDefaultConfigPathFor("IntelliJIdea2020.1")
    return Files.createDirectories(memoryFs.fs.getPath(configPath).normalize())
  }
}