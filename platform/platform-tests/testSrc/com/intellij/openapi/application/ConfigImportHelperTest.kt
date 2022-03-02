// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.plugins.PluginBuilder
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.PlatformTestUtil.useAppConfigDir
import com.intellij.util.SystemProperties
import com.intellij.util.io.createDirectories
import com.intellij.util.io.isDirectory
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Condition
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Predicate

class ConfigImportHelperTest : ConfigImportHelperBaseTest() {
  private val LOG = logger<ConfigImportHelperTest>()

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

  private fun doKeyMapTest(version: String, isMigrationExpected: Boolean) {
    assumeTrue("macOS-only", SystemInfo.isMac)

    val oldConfigDir = createConfigDir(version, product = "DataGrip")
    val newConfigDir = createConfigDir("2019.2", product = "DataGrip")
    ConfigImportHelper.setKeymapIfNeeded(oldConfigDir, newConfigDir, LOG)

    val optionFile = newConfigDir.resolve("${PathManager.OPTIONS_DIRECTORY}/${getPerOsSettingsStorageFolderName()}/keymap.xml")
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

  @Test fun `filtering unwanted files`() {
    val oldConfigDir = createConfigDir("2021.2")
    val newConfigDir = createConfigDir("2021.3")

    val jdkFile = oldConfigDir.resolve("${ApplicationNamesInfo.getInstance().scriptName}.jdk")
    val otherFile = oldConfigDir.resolve("other.xml")
    Files.write(jdkFile, listOf("..."))
    Files.write(otherFile, listOf("..."))

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldConfigDir.resolve("plugins"), newConfigDir.resolve("plugins"), options)

    assertThat(newConfigDir.resolve(jdkFile.fileName)).doesNotExist()
    assertThat(newConfigDir.resolve(otherFile.fileName)).hasSameBinaryContentAs(otherFile)
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
    Registry.get("marketplace.certificate.signature.check").setValue(false, testRootDisposable) // skip verifying plugin certificates
    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.downloadService = object : MarketplacePluginDownloadService() {

      override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator): File {
        val path = localTempDir.newDirectory("pluginTemp")
          .toPath()
          .resolve("my-plugin-new.jar")
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
    options.downloadService = object : MarketplacePluginDownloadService() {

      override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator) =
        throw IOException("404")
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

    val commands = listOf(StartupActionScriptManager.CopyCommand(tempPath, oldPluginsDir.resolve("my-plugin-1.1.jar")))
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

    val commands = listOf(StartupActionScriptManager.CopyCommand(tempPath, oldPluginsDir.resolve("my-plugin-1.1.jar")))
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
    options.downloadService = object : MarketplacePluginDownloadService() {

      override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator) =
        throw AssertionError("No file download should be requested")
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

    val commands = listOf(StartupActionScriptManager.UnzipCommand(tempPath, oldPluginsDir))
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

  @Test fun `filtering custom VM options`() {
    val oldConfigDir = localTempDir.newDirectory("oldConfig").toPath()
    @Suppress("SpellCheckingInspection") val outlaws = listOf(
      "-XX:MaxJavaStackTraceDepth=-1", "-Xverify:none", "-noverify", "-agentlib:yjpagent=opts", "-agentpath:/path/to/lib-yjpagent.so=opts")
    Files.write(oldConfigDir.resolve(VMOptions.getFileName()), outlaws)
    val newConfigDir = localTempDir.newDirectory("newConfig").toPath()

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldConfigDir.resolve("plugins"), newConfigDir.resolve("plugins"), options)

    assertThat(newConfigDir.resolve(VMOptions.getFileName())).hasContent("-XX:MaxJavaStackTraceDepth=10000")
  }

  @Test fun `de-duplicating custom VM options`() {
    val platformOptions = listOf("-Xms128m", "-Xmx750m", "-XX:ReservedCodeCacheSize=512m", "-XX:+UseG1GC")
    val userOptions = listOf("-Xms512m", "-Xmx2g", "-XX:ReservedCodeCacheSize=240m", "-XX:+UseZGC")
    @Suppress("SpellCheckingInspection") val commonOptions = listOf(
      "-XX:SoftRefLRUPolicyMSPerMB=50", "-XX:CICompilerCount=2", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:-OmitStackTraceInFastThrow",
      "-ea", "-Dsun.io.useCanonCaches=false", "-Djdk.http.auth.tunneling.disabledSchemes=\"\"", "-Djdk.attach.allowAttachSelf=true",
      "-Djdk.module.illegalAccess.silent=true", "-Dkotlinx.coroutines.debug=off")

    val platformFile = memoryFs.fs.getPath(VMOptions.getPlatformOptionsFile().toString())
    Files.createDirectories(platformFile.parent)
    Files.write(platformFile, platformOptions + commonOptions)

    val oldConfigDir = createConfigDir("2021.2")
    Files.write(oldConfigDir.resolve(VMOptions.getFileName()), userOptions + commonOptions)
    val newConfigDir = createConfigDir("2021.3")

    val options = ConfigImportHelper.ConfigImportOptions(LOG)
    options.headless = true
    ConfigImportHelper.doImport(oldConfigDir, newConfigDir, null, oldConfigDir.resolve("plugins"), newConfigDir.resolve("plugins"), options)

    assertThat(newConfigDir.resolve(VMOptions.getFileName())).hasContent("-Xms512m\n-Xmx2g\n-XX:+UseZGC")
  }

  @Test fun `finding related directories`() {
    fun populate(config: Path, plugins: Path?, system: Path?, logs: Path?) {
      writeStorageFile(config, System.currentTimeMillis())
      plugins?.createDirectories()
      system?.createDirectories()
      logs?.createDirectories()
    }

    val cfg191 = createConfigDir("2019.1")
    populate(cfg191, null, null, null)
    if (!SystemInfo.isMac) {
      Files.writeString(cfg191.parent.resolve("some_file.txt"), "...")
    }

    val cfg192 = createConfigDir("2019.2")
    populate(cfg192, null, null, null)
    val expected192 = when {
      SystemInfo.isMac -> listOf(cfg192)
      else -> listOf(cfg192.parent)
    }

    val cfg193 = createConfigDir("2019.3")
    val plugins193 = when {
      SystemInfo.isMac -> cfg193.parent.parent.resolve("Application Support").resolve(cfg193.fileName)
      else -> cfg193.resolve("plugins")
    }
    val sys193 = when {
      SystemInfo.isMac -> cfg193.parent.parent.resolve("Caches").resolve(cfg193.fileName)
      else -> cfg193.parent.resolve("system")
    }
    val logs193 = when {
      SystemInfo.isMac -> cfg193.parent.parent.resolve("Logs").resolve(cfg193.fileName)
      else -> sys193.resolve("logs")
    }
    populate(cfg193, plugins193, sys193, logs193)
    val expected193 = when {
      SystemInfo.isMac -> listOf(cfg193, sys193, plugins193, logs193)
      else -> listOf(cfg193.parent)
    }
    val cachesAndLogs193 = when {
      SystemInfo.isMac -> listOf(sys193, logs193)
      else -> listOf(sys193)
    }

    val cfg201 = createConfigDir("2020.1")
    populate(cfg201, null, null, null)

    val cfg202 = createConfigDir("2020.2")
    val sys202 = cfg202.fileSystem.getPath(PathManager.getDefaultSystemPathFor(cfg202.fileName.toString()))
    populate(cfg202, null, sys202, null)

    val cfg203 = createConfigDir("2020.3")
    val sys203 = cfg203.fileSystem.getPath(PathManager.getDefaultSystemPathFor(cfg203.fileName.toString()))
    val plugins203 = cfg203.fileSystem.getPath(PathManager.getDefaultPluginPathFor(cfg203.fileName.toString()))
    val logs203 = cfg203.fileSystem.getPath(PathManager.getDefaultLogPathFor(cfg203.fileName.toString()))
    populate(cfg203, plugins203, sys203, logs203)
    val expected203 = when {
      SystemInfo.isWindows -> listOf(cfg203, sys203)
      SystemInfo.isMac -> listOf(cfg203, sys203, logs203)
      else -> listOf(cfg203, sys203, plugins203)
    }
    val cachesAndLogs203 = when {
      SystemInfo.isMac -> listOf(sys203, logs203)
      else -> listOf(sys203)
    }

    val current = createConfigDir("2021.2")
    val result = ConfigImportHelper.findConfigDirectories(current)
    assertThat(result.paths).containsExactlyInAnyOrder(cfg191, cfg192, cfg193, cfg201, cfg202, cfg203)

    val related = result.paths.map { result.findRelatedDirectories(it, false) }
    assertThat(related).containsExactlyInAnyOrder(
      listOf(cfg191), expected192, expected193, listOf(cfg201), listOf(cfg202, sys202), expected203)

    val cachesAndLogs = result.paths.map { result.findRelatedDirectories(it, true) }.filter { it.isNotEmpty() }
    assertThat(cachesAndLogs).containsExactlyInAnyOrder(
      cachesAndLogs193, listOf(sys202), cachesAndLogs203)
  }

  @Test fun `default project directory is excluded`() {
    val defaultProjectPath = "${SystemProperties.getUserHome()}/PhpstormProjects"
    Files.createDirectories(memoryFs.fs.getPath(defaultProjectPath))
    val current = createConfigDir("2021.2", product = "PhpStorm")
    val result = ConfigImportHelper.findConfigDirectories(current)
    assertThat(result.paths).isEmpty()
  }

  @Test fun `suffix-less directories are excluded`() {
    createConfigDir(product = "Rider", version = "", modern = true)
    val current = createConfigDir(product = "Rider", version = "2022.1")
    val result = ConfigImportHelper.findConfigDirectories(current)
    assertThat(result.paths).isEmpty()
  }

  @Test fun `suffix-less directories are excluded case-insensitively`() {
    createConfigDir(product = "RIDER", version = "", modern = true)
    val current = createConfigDir(product = "Rider", version = "2022.1")
    val result = ConfigImportHelper.findConfigDirectories(current)
    assertThat(result.paths).isEmpty()
  }
}
