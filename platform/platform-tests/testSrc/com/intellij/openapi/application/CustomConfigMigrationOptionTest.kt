// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CustomConfigMigrationOptionTest : ConfigImportHelperBaseTest() {
  @Test
  fun `empty marker file indicates clean config`() {
    val configDir = createMarkerFile("")

    val option = readOption(configDir)
    assertThat(option).isInstanceOf(CustomConfigMigrationOption.StartWithCleanConfig::class.java)
  }

  @Test
  fun `marker file with import path`() {
    val path = PathManager.getDefaultConfigPathFor("IntelliJIdea2019.3")
    Files.createDirectories(memoryFs.fs.getPath(path))
    val configDir = createMarkerFile("import $path")

    val option = readOption(configDir)
    assertThat(option).isInstanceOf(CustomConfigMigrationOption.MigrateFromCustomPlace::class.java)
    assertEquals("Import path parsed incorrectly", path, (option as CustomConfigMigrationOption.MigrateFromCustomPlace).location.toString())
  }
  
  @Test
  fun `marker file with migrate plugins path`() {
    val path = PathManager.getDefaultConfigPathFor("IntelliJIdea2019.3")
    Files.createDirectories(memoryFs.fs.getPath(path))
    val configDir = createMarkerFile("migrate-plugins $path")

    val option = readOption(configDir)
    assertThat(option).isInstanceOf(CustomConfigMigrationOption.MigratePluginsFromCustomPlace::class.java)
    assertEquals(path, (option as CustomConfigMigrationOption.MigratePluginsFromCustomPlace).configLocation.toString())
  }

  @Test
  fun `marker file with properties to set`() {
    val properties = listOf("intellij.first.ide.session", "intellij.config.imported.in.current.session")
    val configDir = createMarkerFile("properties ${properties.joinToString(" ")}")

    val option = readOption(configDir)
    assertThat(option).isInstanceOf(CustomConfigMigrationOption.SetProperties::class.java)
    assertEquals("Properties parsed incorrectly", properties, (option as CustomConfigMigrationOption.SetProperties).properties)
  }

  @Test
  fun `marker file with config merging command`() {
    val configDir = createMarkerFile("merge-configs")

    val option = readOption(configDir)
    assertThat(option).isInstanceOf(CustomConfigMigrationOption.MergeConfigs::class.java)
  }

  private fun readOption(configDir: Path) = CustomConfigMigrationOption.readCustomConfigMigrationOptionAndRemoveMarkerFile(configDir)

  private fun createMarkerFile(content: String): Path {
    val configPath = PathManager.getDefaultConfigPathFor("IntelliJIdea2020.1")
    val configDir = Files.createDirectories(memoryFs.fs.getPath(configPath).normalize())
    Files.writeString(CustomConfigMigrationOption.getCustomConfigMarkerFilePath(configDir), content)
    return configDir
  }
}
