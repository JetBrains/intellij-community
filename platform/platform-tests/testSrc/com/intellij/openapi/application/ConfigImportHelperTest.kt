// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.ide.plugins.PluginBuilder
import com.intellij.ide.plugins.marketplace.MarketplaceRequests
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.PlatformTestUtil.useAppConfigDir
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.SystemProperties
import com.intellij.util.io.isDirectory
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.function.Predicate

private val LOG = logger<ConfigImportHelperTest>()

class ConfigImportHelperTest : BareTestFixtureTestCase() {
  @JvmField @Rule val memoryFs = InMemoryFsRule(SystemInfo.isWindows)
  @JvmField @Rule val localTempDir = TempDirectory()

  @Test fun `config directory is valid for import`() {
    PropertiesComponent.getInstance().setValue("property.ConfigImportHelperTest", true)
    try {
      useAppConfigDir {
        runBlocking { ApplicationManager.getApplication().stateStore.save(forceSavingAllSettings = true) }

        @Suppress("UsePropertyAccessSyntax")
        assertThat(PathManager.getConfigDir())
          .isNotEmptyDirectory()
          .satisfies(Condition(Predicate { ConfigImportHelper.isConfigDirectory(it) }, "A valid config directory"))
      }
    }
    finally {
      PropertiesComponent.getInstance().unsetValue("property.ConfigImportHelperTest")
    }
  }

  @Test fun `find pre-migration config directory`() {
    val cfg201 = createConfigDir("2020.1", modern = false)
    val newConfigPath = createConfigDir("2020.1", modern = true)
    assertThat(findConfigDirectories(newConfigPath)).containsExactly(cfg201)
  }

  @Test fun `find both historic and current config directories`() {
    val cfg15 = createConfigDir("15", storageTS = 1448928000000)
    val cfg193 = createConfigDir("2019.1", storageTS = 1574845200000)
    val cfg201 = createConfigDir("2020.1", storageTS = 1585731600000)

    val newConfigPath = createConfigDir("2020.2")
    assertThat(findConfigDirectories(newConfigPath)).containsExactly(cfg201, cfg193, cfg15)
  }

  @Test fun `find recent config directory`() {
    val cfg201 = createConfigDir("2020.1", storageTS = 100)
    val cfg211 = createConfigDir("2021.1", storageTS = 200)
    val cfg221 = createConfigDir("2022.1", storageTS = 300)

    val newConfigPath = createConfigDir("2022.3")
    assertThat(findConfigDirectories(newConfigPath)).containsExactly(cfg221, cfg211, cfg201)

    writeStorageFile(cfg211, 400)
    assertThat(findConfigDirectories(newConfigPath)).containsExactly(cfg211, cfg221, cfg201)
  }

  @Test fun `sort if no anchor files`() {
    val cfg201 = createConfigDir("2020.1")
    val cfg211 = createConfigDir("2021.1")
    val cfg221 = createConfigDir("2022.1")

    val newConfigPath = createConfigDir("2022.3")
    assertThat(findConfigDirectories(newConfigPath)).containsExactly(cfg221, cfg211, cfg201)
  }

  @Test fun `sort some real historic config dirs`() {
    val cfg10 = createConfigDir("10", product = "DataGrip", storageTS = 1455035096000)
    val cfg162 = createConfigDir("2016.2", product = "DataGrip", storageTS = 1466345662000)
    val cfg161 = createConfigDir("2016.1", product = "DataGrip", storageTS = 1485460719000)
    val cfg171 = createConfigDir("2017.1", product = "DataGrip", storageTS = 1503006763000)
    val cfg172 = createConfigDir("2017.2", product = "DataGrip", storageTS = 1510866492000)
    val cfg163 = createConfigDir("2016.3", product = "DataGrip", storageTS = 1520869009000)
    val cfg181 = createConfigDir("2018.1", product = "DataGrip", storageTS = 1531238044000)
    val cfg183 = createConfigDir("2018.3", product = "DataGrip", storageTS = 1545844523000)
    val cfg182 = createConfigDir("2018.2", product = "DataGrip", storageTS = 1548076635000)
    val cfg191 = createConfigDir("2019.1", product = "DataGrip", storageTS = 1548225505000)
    val cfg173 = createConfigDir("2017.3", product = "DataGrip", storageTS = 1549092322000)

    val newConfigPath = createConfigDir("2020.1", product = "DataGrip")
    assertThat(findConfigDirectories(newConfigPath)).containsExactly(
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

  private fun findConfigDirectories(newConfigPath: Path) = ConfigImportHelper.findConfigDirectories(newConfigPath).map { it.path }

  private fun doKeyMapTest(version: String, isMigrationExpected: Boolean) {
    assumeTrue("macOS-only", SystemInfo.isMac)

    val oldConfigDir = createConfigDir(version, product = "DataGrip")
    val newConfigDir = createConfigDir("2019.2", product = "DataGrip")
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

  @Test fun `migrate plugins to empty directory`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    PluginBuilder().depends("com.intellij.modules.lang").buildJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir).isDirectoryContaining { it.fileName.toString() == "my-plugin.jar" }
  }

  @Test fun `download incompatible plugin`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldBuilder = PluginBuilder()
      .depends("com.intellij.modules.lang")
      .untilBuild("193.1")
      .buildJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.marketplaceRequests = object : MarketplaceRequests() {
      override fun download(pluginUrl: String, indicator: ProgressIndicator): File {
        val path = localTempDir.newDirectory("pluginTemp").toPath().resolve("my-plugin-new.jar")
        PluginBuilder()
          .id(oldBuilder.id)
          .buildJar(path)
        return path.toFile()
      }
    }
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir).isDirectoryContaining { it.fileName.toString() == "my-plugin-new.jar" }
  }

  @Test fun `keep incompatible plugin if can't download compatible`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    PluginBuilder()
      .untilBuild("193.1")
      .buildJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.marketplaceRequests = object : MarketplaceRequests() {
      override fun download(pluginUrl: String, indicator: ProgressIndicator): File {
        throw IOException("404")
      }
    }
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir).isDirectoryContaining { it.fileName.toString() == "my-plugin.jar" }
  }

  @Test fun `skip bundled plugins`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldBundledPluginsDir = localTempDir.newDirectory("oldBundled").toPath()
    val bundledBuilder = PluginBuilder().version("1.1").buildJar(oldBundledPluginsDir.resolve("my-plugin-bundled.jar"))
    PluginBuilder()
      .id(bundledBuilder.id)
      .version("1.0")
      .buildJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    options.bundledPluginPath = oldBundledPluginsDir
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir).doesNotExist()
  }

  @Test fun `skip broken plugins`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val builder = PluginBuilder()
      .version("1.0")
      .buildJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    options.brokenPluginVersions = mapOf(PluginId.getId(builder.id) to setOf("1.0"))

    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir).doesNotExist()
  }

  @Test fun `skip pending upgrades`() {
    val oldConfigDir = localTempDir.newDirectory("old/config").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldPluginsTempDir = localTempDir.newDirectory("old/system/plugins").toPath()

    val tempPath = oldPluginsTempDir.resolve("my-plugin.jar")
    val tempBuilder = PluginBuilder()
      .version("1.1")
      .buildJar(tempPath)

    val commands = mutableListOf<StartupActionScriptManager.ActionCommand>()
    commands.add(StartupActionScriptManager.CopyCommand(tempPath.toFile(), oldPluginsDir.resolve("my-plugin-1.1.jar").toFile()))
    StartupActionScriptManager.saveActionScript(commands, oldPluginsTempDir.resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE))

    PluginBuilder()
      .id(tempBuilder.id)
      .version("1.0")
      .buildJar(oldPluginsDir.resolve("my-plugin-1.0.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir)
      .isDirectoryContaining { it.fileName.toString() == "my-plugin-1.1.jar" }
      .isDirectoryNotContaining { it.fileName.toString() == "my-plugin-1.0.jar" }
  }

  @Test fun `do not download updates for plugins with pending updates`() {
    val oldConfigDir = localTempDir.newDirectory("old/config").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldPluginsTempDir = localTempDir.newDirectory("old/system/plugins").toPath()

    val tempPath = oldPluginsTempDir.resolve("my-plugin.jar")
    val tempBuilder = PluginBuilder()
      .version("1.1")
      .buildJar(tempPath)

    val commands = mutableListOf<StartupActionScriptManager.ActionCommand>()
    commands.add(StartupActionScriptManager.CopyCommand(tempPath.toFile(), oldPluginsDir.resolve("my-plugin-1.1.jar").toFile()))
    StartupActionScriptManager.saveActionScript(commands, oldPluginsTempDir.resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE))

    PluginBuilder()
      .id(tempBuilder.id)
      .version("1.0")
      .untilBuild("193.1")
      .buildJar(oldPluginsDir.resolve("my-plugin-1.0.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.marketplaceRequests = object : MarketplaceRequests() {
      override fun download(pluginUrl: String, indicator: ProgressIndicator): File {
        throw AssertionError("No file download should be requested")
      }
    }
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir)
      .isDirectoryContaining { it.fileName.toString() == "my-plugin-1.1.jar" }
      .isDirectoryNotContaining { it.fileName.toString() == "my-plugin-1.0.jar" }
  }

  @Test fun `skip pending upgrades for plugin zips`() {
    val oldConfigDir = localTempDir.newDirectory("old/config").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldPluginsTempDir = localTempDir.newDirectory("old/system/plugins").toPath()

    val tempPath = oldPluginsTempDir.resolve("my-plugin.zip")
    val tempBuilder = PluginBuilder()
      .version("1.1")
      .buildZip(tempPath)

    val commands = mutableListOf<StartupActionScriptManager.ActionCommand>()
    commands.add(StartupActionScriptManager.UnzipCommand(tempPath.toFile(), oldPluginsDir.toFile()))
    StartupActionScriptManager.saveActionScript(commands, oldPluginsTempDir.resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE))

    PluginBuilder()
      .id(tempBuilder.id)
      .version("1.0")
      .buildJar(oldPluginsDir.resolve("my-plugin-1.0.jar"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = newConfigDir.resolve("plugins")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)
    assertThat(newPluginsDir)
      .isDirectoryContaining { it.fileName.toString() == tempBuilder.id && it.isDirectory() }
      .isDirectoryNotContaining { it.fileName.toString() == "my-plugin-1.0.jar" }
  }


  @Test fun `do not migrate plugins to existing directory`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    val oldPluginsDir = Files.createDirectories(oldConfigDir.resolve("plugins"))
    val oldPluginZip = Files.createFile(oldPluginsDir.resolve("old-plugin.zip"))

    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()
    val newPluginsDir = Files.createDirectories(newConfigDir.resolve("plugins"))
    val newPluginZip = Files.createFile(newPluginsDir.resolve("new-plugin.zip"))

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldPluginsDir, newPluginsDir, options)

    assertThat(newPluginsDir)
      .isDirectoryContaining { it.fileName == newPluginZip.fileName }
      .isDirectoryNotContaining { it.fileName == oldPluginZip.fileName }
  }

  private fun createConfigDir(version: String, modern: Boolean = version >= "2020.1", product: String = "IntelliJIdea", storageTS: Long = 0): Path {
    val path = when {
      modern -> PathManager.getDefaultConfigPathFor("${product}${version}")
      SystemInfo.isMac -> "${SystemProperties.getUserHome()}/Library/Preferences/${product}${version}"
      else -> "${SystemProperties.getUserHome()}/.${product}${version}/config"
    }
    val dir = Files.createDirectories(memoryFs.fs.getPath(path).normalize())
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