// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil.useAppConfigDir
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.SystemProperties
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.function.Predicate

private val LOG = logger<ConfigImportHelperTest>()

class ConfigImportHelperTest : BareTestFixtureTestCase() {
  @JvmField @Rule val memoryFs = InMemoryFsRule()
  @JvmField @Rule val localTempDir = TempDirectory()

  @Test fun `config directory is valid for import`() {
    PropertiesComponent.getInstance().setValue("property.ConfigImportHelperTest", true)
    try {
      useAppConfigDir {
        runBlocking { ApplicationManager.getApplication().stateStore.save(forceSavingAllSettings = true) }

        assertThat(Paths.get(PathManager.getConfigPath()))
          .isNotEmptyDirectory()
          .satisfies(Condition(Predicate<Path> { ConfigImportHelper.isConfigDirectory(it) }, "A valid config directory"))
      }
    }
    finally {
      PropertiesComponent.getInstance().unsetValue("property.ConfigImportHelperTest")
    }
  }

  @Test fun `find recent config directory`() {
    val cfg201 = createConfigDir("2020.1", storageTS = 100)
    val cfg211 = createConfigDir("2021.1", storageTS = 200)
    val cfg221 = createConfigDir("2022.1", storageTS = 300)

    val newConfigPath = createConfigDir("2022.3")
    assertThat(ConfigImportHelper.findConfigDirectories(newConfigPath)).containsExactly(cfg221, cfg211, cfg201)

    writeStorageFile(cfg211, 400)
    assertThat(ConfigImportHelper.findConfigDirectories(newConfigPath)).containsExactly(cfg211, cfg221, cfg201)
  }

  @Test fun `sort if no anchor files`() {
    val cfg201 = createConfigDir("2020.1")
    val cfg211 = createConfigDir("2021.1")
    val cfg221 = createConfigDir("2022.1")

    val newConfigPath = createConfigDir("2022.3")
    assertThat(ConfigImportHelper.findConfigDirectories(newConfigPath)).containsExactly(cfg221, cfg211, cfg201)
  }

  @Test fun `sort some real historic config dirs`() {
    val cfg10 = createConfigDir("10", "DataGrip", 1455035096000)
    val cfg162 = createConfigDir("2016.2", "DataGrip", 1466345662000)
    val cfg161 = createConfigDir("2016.1", "DataGrip", 1485460719000)
    val cfg171 = createConfigDir("2017.1", "DataGrip", 1503006763000)
    val cfg172 = createConfigDir("2017.2", "DataGrip", 1510866492000)
    val cfg163 = createConfigDir("2016.3", "DataGrip", 1520869009000)
    val cfg181 = createConfigDir("2018.1", "DataGrip", 1531238044000)
    val cfg183 = createConfigDir("2018.3", "DataGrip", 1545844523000)
    val cfg182 = createConfigDir("2018.2", "DataGrip", 1548076635000)
    val cfg191 = createConfigDir("2019.1", "DataGrip", 1548225505000)
    val cfg173 = createConfigDir("2017.3", "DataGrip", 1549092322000)

    val newConfigPath = createConfigDir("2019.2", "DataGrip")
    assertThat(ConfigImportHelper.findConfigDirectories(newConfigPath)).containsExactly(
      cfg173, cfg191, cfg182, cfg183, cfg181, cfg163, cfg172, cfg171, cfg161, cfg162, cfg10)
  }

  @Test fun `set keymap - old version`() {
    doKeyMapTest("2016.4", isMigrationExpected = true)
    doKeyMapTest("2019.1", isMigrationExpected = true)
  }

  @Test fun `set keymap - new version`() {
    doKeyMapTest("2019.2", isMigrationExpected = false)
    doKeyMapTest("2019.3", isMigrationExpected = false)
  }

  private fun doKeyMapTest(version: String, isMigrationExpected: Boolean) {
    assumeTrue("macOS-only", SystemInfo.isMac)

    val oldConfigDir = createConfigDir(version, "DataGrip")
    val newConfigDir = createConfigDir("2019.2", "DataGrip")
    ConfigImportHelper.setKeymapIfNeeded(oldConfigDir, newConfigDir, LOG)

    val optionFile = newConfigDir.resolve("${PathManager.OPTIONS_DIRECTORY}/keymap.xml")
    if (isMigrationExpected) {
      assertThat(optionFile).usingCharset(StandardCharsets.UTF_8).hasContent("""
        <application>
          <component name="KeymapManager">
            <active_keymap name="Mac OS X" />
          </component>
        </application>
      """.trimIndent())
    }
    else {
      assertThat(optionFile).doesNotExist()
    }
  }

  @Test fun `migrate settings to empty directory`() {
    val oldConfigDir = localTempDir.newFolder("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldPluginZip = Files.createFile(oldPluginsDir.resolve("my-plugin.zip"))

    val newConfigDir = localTempDir.newFolder("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, LOG)
    assertThat(newPluginsDir).isDirectoryContaining { it.fileName == oldPluginZip.fileName }
  }

  @Test fun `migrate settings to directory with existing plugin`() {
    val oldConfigDir = localTempDir.newFolder("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldPluginZip = Files.createFile(oldPluginsDir.resolve("old-plugin.zip"))

    val newConfigDir = localTempDir.newFolder("newConfig").toPath()
    val newPluginsDir = Files.createDirectories(newConfigDir.resolve("plugins"))
    val newPluginZip = Files.createFile(newPluginsDir.resolve("new-plugin.zip"))

    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, LOG)

    assertThat(newPluginsDir)
      .isDirectoryContaining { it.fileName == newPluginZip.fileName }
      .isDirectoryNotContaining { it.fileName == oldPluginZip.fileName }
  }

  private fun createConfigDir(version: String, product: String = "IntelliJIdea", storageTS: Long = 0): Path {
    val defaultPath = PathManager.getDefaultConfigPathFor("${product}${version}")
    val relative = defaultPath.removePrefix(SystemProperties.getUserHome() + File.separatorChar)
    val dir = Files.createDirectories(memoryFs.fs.getPath("/data/$relative"))
    if (storageTS > 0) writeStorageFile(dir, storageTS)
    return dir
  }

  private fun writeStorageFile(config: Path, lastModified: Long) {
    val file = config.resolve("${PathManager.OPTIONS_DIRECTORY}/${StoragePathMacros.NON_ROAMABLE_FILE}")
    Files.createDirectories(file.parent)
    Files.write(file, "<application/>".toByteArray())
    Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified))
  }
}