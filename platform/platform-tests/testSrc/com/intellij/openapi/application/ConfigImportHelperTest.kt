// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.configurationStore.getPerOsSettingsStorageFolderName
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.ConfigImportOptions
import com.intellij.ide.ConfigImportSettings
import com.intellij.ide.SpecialConfigFiles
import com.intellij.ide.plugins.DisabledPluginsState.Companion.saveDisabledPluginsAndInvalidate
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.marketplace.MarketplacePluginDownloadService
import com.intellij.ide.plugins.marketplace.utils.MarketplaceCustomizationService
import com.intellij.ide.startup.StartupActionScriptManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.observable.util.setSystemProperty
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.testFramework.plugins.buildMainJar
import com.intellij.platform.testFramework.plugins.buildZip
import com.intellij.platform.testFramework.plugins.dependsIntellijModulesLang
import com.intellij.platform.testFramework.plugins.plugin
import com.intellij.testFramework.PlatformTestUtil.useAppConfigDir
import com.intellij.testFramework.PlatformTestUtil.withSystemProperty
import com.intellij.testFramework.junit5.http.url
import com.intellij.testFramework.replaceService
import com.intellij.util.SystemProperties
import com.intellij.util.queryParameters
import com.intellij.util.system.OS
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Condition
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function
import java.util.function.Predicate
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteRecursively
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.writeLines
import kotlin.io.path.writeText

private val LOG = logger<ConfigImportHelperTest>()

@OptIn(ExperimentalPathApi::class)
class ConfigImportHelperTest : ConfigImportHelperBaseTest() {
  val options = ConfigImportOptions(LOG).apply { headless = true }

  @Test fun `config directory is valid for import`() {
    PropertiesComponent.getInstance().setValue("property.ConfigImportHelperTest", true)
    try {
      useAppConfigDir {
        runBlocking { ApplicationManager.getApplication().stateStore.save(forceSavingAllSettings = true) }

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
    assertThat(findConfigDirectories(newConfigPath).paths).containsExactly(cfg201)
  }

  @Test fun `find both historic and current config directories`() {
    val cfg15 = createConfigDir("15", storageTS = LocalDateTime.of(2015, 12, 1, 10, 0))
    val cfg193 = createConfigDir("2019.1", storageTS = LocalDateTime.of(2019, 11, 27, 10, 0))
    val cfg201 = createConfigDir("2020.1", storageTS = LocalDateTime.of(2020, 4, 1, 10, 0))

    val newConfigPath = createConfigDir("2020.2")
    assertThat(findConfigDirectories(newConfigPath).paths).containsExactly(cfg201, cfg193, cfg15)
  }

  @Test fun `find recent config directory`() {
    val now = LocalDateTime.now()
    val cfg201 = createConfigDir("2020.1", storageTS = now.minusSeconds(3))
    val cfg211 = createConfigDir("2021.1", storageTS = now.minusSeconds(2))
    val cfg221 = createConfigDir("2022.1", storageTS = now.minusSeconds(1))

    val newConfigPath = createConfigDir("2022.3")
    assertThat(findConfigDirectories(newConfigPath).paths).containsExactly(cfg221, cfg211, cfg201)

    writeStorageFile(cfg211, now)
    assertThat(findConfigDirectories(newConfigPath).paths).containsExactly(cfg211, cfg221, cfg201)
  }

  @Test fun `sort if no anchor files`() {
    val cfg201 = createConfigDir("2020.1")
    val cfg211 = createConfigDir("2021.1")
    val cfg221 = createConfigDir("2022.1")

    val newConfigPath = createConfigDir("2022.3")
    assertThat(findConfigDirectories(newConfigPath).paths).containsExactly(cfg221, cfg211, cfg201)
  }

  @Test fun `sort some real historic config dirs`() {
    val cfg10 = createConfigDir("10", product = "DataGrip", storageTS = LocalDateTime.of(2016, 2, 9, 17, 24))
    val cfg162 = createConfigDir("2016.2", product = "DataGrip", storageTS = LocalDateTime.of(2016, 6, 19, 16, 14))
    val cfg161 = createConfigDir("2016.1", product = "DataGrip", storageTS = LocalDateTime.of(2017, 1, 26, 20, 58))
    val cfg171 = createConfigDir("2017.1", product = "DataGrip", storageTS = LocalDateTime.of(2017, 8, 17, 23, 52))
    val cfg172 = createConfigDir("2017.2", product = "DataGrip", storageTS = LocalDateTime.of(2017, 11, 16, 22, 8))
    val cfg163 = createConfigDir("2016.3", product = "DataGrip", storageTS = LocalDateTime.of(2018, 3, 12, 16, 36))
    val cfg181 = createConfigDir("2018.1", product = "DataGrip", storageTS = LocalDateTime.of(2018, 7, 10, 17, 54))
    val cfg183 = createConfigDir("2018.3", product = "DataGrip", storageTS = LocalDateTime.of(2018, 12, 26, 18, 23))
    val cfg182 = createConfigDir("2018.2", product = "DataGrip", storageTS = LocalDateTime.of(2019, 1, 21, 14, 17))
    val cfg191 = createConfigDir("2019.1", product = "DataGrip", storageTS = LocalDateTime.of(2019, 1, 23, 7, 38))
    val cfg173 = createConfigDir("2017.3", product = "DataGrip", storageTS = LocalDateTime.of(2019, 2, 2, 8, 52))

    val newConfigPath = createConfigDir("2020.1", product = "DataGrip")
    assertThat(findConfigDirectories(newConfigPath).paths).containsExactly(
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
    assumeTrue("macOS-only", OS.CURRENT == OS.macOS)

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

    val jdkFile = oldConfigDir.resolve("${ApplicationNamesInfo.getInstance().scriptName}.jdk").apply { writeText("...") }
    val migrationOptionFile = CustomConfigMigrationOption.getCustomConfigMarkerFilePath(oldConfigDir).createFile()
    val lockFile = oldConfigDir.resolve(SpecialConfigFiles.LOCK_FILE).apply { writeText("...") }
    val otherFile =oldConfigDir.resolve("other.xml").apply { writeText("...") }

    doImport(oldConfigDir, newConfigDir, options)

    assertThat(newConfigDir.resolve(jdkFile.fileName)).doesNotExist()
    assertThat(newConfigDir.resolve(migrationOptionFile.fileName)).doesNotExist()
    assertThat(newConfigDir.resolve(lockFile.fileName)).doesNotExist()
    assertThat(newConfigDir.resolve(otherFile.fileName)).hasSameBinaryContentAs(otherFile)
  }

  @Test fun `nested files should be passed to the settings filter`() {
    val oldConfigDir = createConfigDir("2022.3")
    val newConfigDir = createConfigDir("2023.1")

    val subdir = oldConfigDir.resolve("subdir").createDirectories()
    listOf(
      subdir.resolve("file1.txt"),
      subdir.resolve("file2.txt")
    ).forEach { it.writeLines(listOf("...")) }

    options.importSettings = object : ConfigImportSettings {
      override fun shouldSkipPath(path: Path) = path.endsWith("file1.txt")
    }
    doImport(oldConfigDir, newConfigDir, options)

    assertThat(newConfigDir.resolve("subdir")).isDirectory()
    assertThat(newConfigDir.resolve("subdir/file1.txt")).doesNotExist()
    assertThat(newConfigDir.resolve("subdir/file2.txt")).isRegularFile()
  }

  @Test fun `migrate plugins to empty directory`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    plugin("my-plugin") { dependsIntellijModulesLang() }.buildMainJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")
    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir).isDirectoryContaining { it.name == "my-plugin.jar" }
  }

  @Test fun `download incompatible plugin`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    plugin("my-plugin") {
      dependsIntellijModulesLang()
      untilBuild = "193.1"
    }.buildMainJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")
    Registry.get("marketplace.certificate.signature.check").setValue(false, testRootDisposable) // skip verifying plugin certificates
    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.downloadService = object : MarketplacePluginDownloadService() {
      override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator?): Path {
        val path = newTempDir("pluginTemp").resolve("my-plugin-new.jar")
        plugin("my-plugin") { dependsIntellijModulesLang() }.buildMainJar(path)
        return path
      }
    }

    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir).isDirectoryContaining { it.name == "my-plugin-new.jar" }
  }

  @Test fun `keep incompatible plugin if can't download compatible`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    plugin("my-plugin") {
      dependsIntellijModulesLang()
      untilBuild = "193.1"
    }.buildMainJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.downloadService = object : MarketplacePluginDownloadService() {
      override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator?) = throw IOException("404")
    }
    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir).isDirectoryContaining { it.name == "my-plugin.jar" }
  }

  @Test fun `skip bundled plugins`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    val oldBundledPluginsDir = newTempDir("oldBundled")
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.1" }.buildMainJar(oldBundledPluginsDir.resolve("my-plugin-bundled.jar"))
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.0" }.buildMainJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    options.bundledPluginPath = oldBundledPluginsDir
    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir).doesNotExist()
  }

  @Test fun `skip broken plugins`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.0" }.buildMainJar(oldPluginsDir.resolve("my-plugin.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    ConfigImportHelper.testBrokenPluginsFetcherStub = Function { mapOf(PluginId.getId("my-plugin") to setOf("1.0")) }
    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir).doesNotExist()
  }

  @Test fun `skip pending upgrades`() {
    val oldConfigDir = newTempDir("old/config")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    val oldPluginsTempDir = newTempDir("old/system/plugins")

    val tempPath = oldPluginsTempDir.resolve("my-plugin.jar")
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.1" }.buildMainJar(tempPath)

    val commands = listOf(StartupActionScriptManager.CopyCommand(tempPath, oldPluginsDir.resolve("my-plugin-1.1.jar")))
    StartupActionScriptManager.saveActionScript(commands, oldPluginsTempDir.resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE))

    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.0" }.buildMainJar(oldPluginsDir.resolve("my-plugin-1.0.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir)
      .isDirectoryContaining { it.name == "my-plugin-1.1.jar" }
      .isDirectoryNotContaining { it.name == "my-plugin-1.0.jar" }
  }

  @Test fun `do not download updates for plugins with pending updates`() {
    val oldConfigDir = newTempDir("old/config")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    val oldPluginsTempDir = newTempDir("old/system/plugins")

    val tempPath = oldPluginsTempDir.resolve("my-plugin.jar")
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.1" }.buildMainJar(tempPath)

    val commands = listOf(StartupActionScriptManager.CopyCommand(tempPath, oldPluginsDir.resolve("my-plugin-1.1.jar")))
    StartupActionScriptManager.saveActionScript(commands, oldPluginsTempDir.resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE))

    plugin("my-plugin") {
      dependsIntellijModulesLang()
      version = "1.0"
      untilBuild = "193.1"
    }.buildMainJar(oldPluginsDir.resolve("my-plugin-1.0.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    options.compatibleBuildNumber = BuildNumber.fromString("201.1")
    options.downloadService = object : MarketplacePluginDownloadService() {
      override fun downloadPlugin(pluginUrl: String, indicator: ProgressIndicator?) =
        throw AssertionError("No file download should be requested")
    }
    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir)
      .isDirectoryContaining { it.name == "my-plugin-1.1.jar" }
      .isDirectoryNotContaining { it.name == "my-plugin-1.0.jar" }
  }

  @Test fun `skip pending upgrades for plugin zips`() {
    val oldConfigDir = newTempDir("old/config")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    val oldPluginsTempDir = newTempDir("old/system/plugins")

    val tempPath = oldPluginsTempDir.resolve("my-plugin.zip")
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.1" }.buildZip(tempPath)

    val commands = listOf(StartupActionScriptManager.UnzipCommand(tempPath, oldPluginsDir))
    StartupActionScriptManager.saveActionScript(commands, oldPluginsTempDir.resolve(StartupActionScriptManager.ACTION_SCRIPT_FILE))

    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.0" }.buildMainJar(oldPluginsDir.resolve("my-plugin-1.0.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newPluginsDir)
      .isDirectoryContaining { it.name == "my-plugin" && it.isDirectory() }
      .isDirectoryNotContaining { it.name == "my-plugin-1.0.jar" }
  }

  @Test fun `do not migrate plugins to existing directory`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    val oldPluginZip = oldPluginsDir.resolve("old-plugin.zip").createFile()

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins").createDirectories()
    val newPluginZip = newPluginsDir.resolve("new-plugin.zip").createFile()

    doImport(oldConfigDir, newConfigDir, options)

    assertThat(newPluginsDir)
      .isDirectoryContaining { it.fileName == newPluginZip.fileName }
      .isDirectoryNotContaining { it.fileName == oldPluginZip.fileName }
  }

  @Test fun `filtering custom VM options`() {
    val oldConfigDir = newTempDir("oldConfig")
    @Suppress("SpellCheckingInspection") val outlaws = listOf(
      "-XX:MaxJavaStackTraceDepth=-1", "-Xverify:none", "-noverify", "-agentlib:yjpagent=opts", "-agentpath:/path/to/lib-yjpagent.so=opts")
    oldConfigDir.resolve(VMOptions.getFileName()).writeLines(outlaws)
    val newConfigDir = newTempDir("newConfig")

    doImport(oldConfigDir, newConfigDir, options)

    assertThat(newConfigDir.resolve(VMOptions.getFileName())).hasContent("-XX:MaxJavaStackTraceDepth=10000")
  }

  @Test fun `de-duplicating custom VM options`() {
    val platformOptions = listOf("-Xms128m", "-Xmx750m", "-XX:ReservedCodeCacheSize=512m", "-XX:+UseG1GC")
    val userOptions = listOf("-Xms512m", "-Xmx2g", "-XX:ReservedCodeCacheSize=240m", "-XX:+UseZGC")
    val commonOptions = listOf(
      "-XX:SoftRefLRUPolicyMSPerMB=50", "-XX:CICompilerCount=2", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:-OmitStackTraceInFastThrow",
      "-ea", "-Dsun.io.useCanonCaches=false", "-Djdk.http.auth.tunneling.disabledSchemes=\"\"", "-Djdk.attach.allowAttachSelf=true",
      "-Djdk.module.illegalAccess.silent=true", "-Dkotlinx.coroutines.debug=off")

    memoryFs.fs.getPath(VMOptions.getPlatformOptionsFile().toString())
      .createParentDirectories()
      .writeLines(platformOptions + commonOptions)

    val oldConfigDir = createConfigDir("2021.2")
    oldConfigDir.resolve(VMOptions.getFileName()).writeLines(userOptions + commonOptions)
    val newConfigDir = createConfigDir("2021.3")

    doImport(oldConfigDir, newConfigDir, options)

    assertThat(newConfigDir.resolve(VMOptions.getFileName())).hasContent("-Xms512m\n-Xmx2g\n-XX:+UseZGC")
  }

  @Test fun `filtering FLS VM option when importing from CE`() {
    val oldICConfigDir = createConfigDir(version = "2025.2", product = "IdeaIC")
    val oldIUConfigDir = createConfigDir(version = "2025.2", product = "IntelliJIdea")
    listOf(oldICConfigDir, oldIUConfigDir).forEach {
      it.resolve(VMOptions.getFileName())
        .createParentDirectories()
        .writeLines(listOf("-Dsome.random.properrty=xyz", "-DJETBRAINS_LICENSE_SERVER=host.domain"))
    }

    val newConfigDir = createConfigDir(version = "2025.3")
    val importedFile = newConfigDir.resolve(VMOptions.getFileName())

    newConfigDir.apply { deleteRecursively() }.createDirectories()
    doImport(oldICConfigDir, newConfigDir, options)
    assertThat(importedFile).content().doesNotContain("JETBRAINS_LICENSE_SERVER")

    newConfigDir.apply { deleteRecursively() }.createDirectories()
    doImport(oldIUConfigDir, newConfigDir, options)
    assertThat(importedFile).content().contains("JETBRAINS_LICENSE_SERVER")
  }

  @Test fun `finding related directories`() {
    fun populate(config: Path, plugins: Path?, system: Path?, logs: Path?) {
      writeStorageFile(config, LocalDateTime.now())
      plugins?.createDirectories()
      system?.createDirectories()
      logs?.createDirectories()
    }

    val cfg191 = createConfigDir("2019.1")
    populate(cfg191, null, null, null)
    if (OS.CURRENT != OS.macOS) {
      cfg191.resolveSibling("some_file.txt").writeText("...")
    }

    val cfg192 = createConfigDir("2019.2")
    populate(cfg192, null, null, null)
    val expected192 = when (OS.CURRENT) {
      OS.macOS -> listOf(cfg192)
      else -> listOf(cfg192.parent)
    }

    val cfg193 = createConfigDir("2019.3")
    val plugins193 = when (OS.CURRENT) {
      OS.macOS -> cfg193.parent.resolveSibling("Application Support").resolve(cfg193.fileName)
      else -> cfg193.resolve("plugins")
    }
    val sys193 = when (OS.CURRENT) {
      OS.macOS -> cfg193.parent.resolveSibling("Caches").resolve(cfg193.fileName)
      else -> cfg193.resolveSibling("system")
    }
    val logs193 = when (OS.CURRENT) {
      OS.macOS -> cfg193.parent.resolveSibling("Logs").resolve(cfg193.fileName)
      else -> sys193.resolve("logs")
    }
    populate(cfg193, plugins193, sys193, logs193)
    val expected193 = when (OS.CURRENT) {
      OS.macOS -> listOf(cfg193, sys193, plugins193, logs193)
      else -> listOf(cfg193.parent)
    }
    val cachesAndLogs193 = when (OS.CURRENT) {
      OS.macOS -> listOf(sys193, logs193)
      else -> listOf(sys193)
    }

    val cfg201 = createConfigDir("2020.1")
    populate(cfg201, null, null, null)

    val cfg202 = createConfigDir("2020.2")
    val sys202 = cfg202.fileSystem.getPath(PathManager.getDefaultSystemPathFor(cfg202.name))
    populate(cfg202, null, sys202, null)

    val cfg203 = createConfigDir("2020.3")
    val sys203 = cfg203.fileSystem.getPath(PathManager.getDefaultSystemPathFor(cfg203.name))
    val plugins203 = cfg203.fileSystem.getPath(PathManager.getDefaultPluginPathFor(cfg203.name))
    val logs203 = cfg203.fileSystem.getPath(PathManager.getDefaultLogPathFor(cfg203.name))
    populate(cfg203, plugins203, sys203, logs203)
    val expected203 = when (OS.CURRENT) {
      OS.Windows -> listOf(cfg203, sys203)
      OS.macOS -> listOf(cfg203, sys203, logs203)
      else -> listOf(cfg203, sys203, plugins203)
    }
    val cachesAndLogs203 = when {
      OS.CURRENT == OS.macOS -> listOf(sys203, logs203)
      else -> listOf(sys203)
    }

    val current = createConfigDir("2021.2")
    val result = findConfigDirectories(current)
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
    memoryFs.fs.getPath(defaultProjectPath).createDirectories()
    val current = createConfigDir("2021.2", product = "PhpStorm")
    assertThat(findConfigDirectories(current).paths).isEmpty()
  }

  @Test fun `suffix-less directories are excluded`() {
    createConfigDir(product = "Rider", version = "", modern = true)
    val current = createConfigDir(product = "Rider", version = "2022.1")
    assertThat(findConfigDirectories(current).paths).isEmpty()
  }

  @Test fun `suffix-less directories are excluded case-insensitively`() {
    createConfigDir(product = "RIDER", version = "", modern = true)
    val current = createConfigDir(product = "Rider", version = "2022.1")
    assertThat(findConfigDirectories(current).paths).isEmpty()
  }

  @Test fun `non-versioned directories are excluded_Rider`() {
    createConfigDir(product = "RiderFlow", version = "", modern = true)
    createConfigDir(product = "RiderRemoteDebugger", version = "", modern = true)
    val current = createConfigDir(product = "Rider", version = "2023.2")
    assertThat(findConfigDirectories(current).paths).isEmpty()
  }

  @Test fun `non-versioned directories are excluded_CLion`() {
    createConfigDir(product = ".clion-vcpkg", version = "", modern = false) // was created at the user dir by older versions
    createConfigDir(product = "CLionNova", version = "2023.2", modern = true) // "CLion" + RADLER_SUFFIX = "CLionNova"
    val current = createConfigDir(product = "CLion", version = "2023.2")
    assertThat(findConfigDirectories(current).paths).isEmpty()
  }

  @Test fun `merging VM options`() {
    val oldConfigDir = createConfigDir(version = "2023.1")
    val oldVmOptionsFile = oldConfigDir.resolve(VMOptions.getFileName()).writeLines(listOf("-Xmx4g", "-Dsome.prop=old.val"))
    val newConfigDir = createConfigDir(version = "2023.2")
    val newVmOptionsFile = newConfigDir.resolve(VMOptions.getFileName()).writeLines(listOf("-Xmx2048m", "-Dsome.prop=new.val"))
    options.mergeVmOptions = true

    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newVmOptionsFile.readLines()).containsExactly("-Xmx4g", "-Dsome.prop=new.val")

    oldVmOptionsFile.writeLines(listOf("-Xmx1g"))
    newVmOptionsFile.writeLines(listOf("-Xmx2048m", "-Dunique.prop=some.val"))

    doImport(oldConfigDir, newConfigDir, options)
    assertThat(newVmOptionsFile.readLines()).containsExactly("-Xmx2048m", "-Dunique.prop=some.val")
  }

  @TestFor(issues = ["IDEA-341860"])
  @Test fun `don't ask for VM options restart, if they are actual`() {
    val oldConfigDir = newTempDir("oldConfig")
    oldConfigDir.resolve(PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NON_ROAMABLE_FILE)
      .createParentDirectories()
      .writeLines(listOf("aaaa"))
    oldConfigDir.resolve(VMOptions.getFileName()).writeLines(listOf("-Xmx2048m", "-Dsome.prop=old.val"))

    val newConfigDir = newTempDir("newConfig")
    val newVmOptionsFile = newConfigDir.resolve(VMOptions.getFileName()).writeLines(listOf("-Xmx3G", "-Dsome.prop=new.val"))

    newConfigDir.fileSystem.getPath(VMOptions.getPlatformOptionsFile().toString())
      .createParentDirectories()
      .writeLines(listOf("-Xmx2048m", "-Dsome.another.prop=another.val"))

    CustomConfigMigrationOption.MigrateFromCustomPlace(oldConfigDir).writeConfigMarkerFile(newConfigDir)
    ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
    assertThat(newVmOptionsFile.readLines()).containsExactly("-Xmx3G", "-Dsome.prop=new.val")
  }

  @Test fun `finding inherited directory`() {
    val oldConfigDir = createConfigDir("2025.2", storageTS = LocalDateTime.now())
    val newConfigDir = newTempDir("newConfig")

    assertThat(findInheritedDirectory(newConfigDir, null)).isNull()
    assertThat(findInheritedDirectory(newConfigDir, newConfigDir.toString())).isNull()
    assertThat(findInheritedDirectory(newConfigDir, newConfigDir.resolveSibling("_missing").toString())).isNull()

    assertThat(findInheritedDirectory(newConfigDir, oldConfigDir.toString())!!.paths).containsExactly(oldConfigDir)
  }

  @Test fun `automatic import`() {
    val oldConfigDir = createConfigDir("2025.2", storageTS = LocalDateTime.now())
    val markerFile = createTempFile(oldConfigDir.resolve(PathManager.OPTIONS_DIRECTORY), "test.", ".xml")
    val newConfigDir = createConfigDir(version = "2025.3")

    withSystemProperty<RuntimeException>("intellij.startup.wizard", "false") {
      ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
    }

    assertThat(newConfigDir.resolve(PathManager.OPTIONS_DIRECTORY)).isDirectoryContaining { it.name == markerFile.name }
  }

  @Test fun `skipping automatic import into custom directory`() {
    createConfigDir("2025.2", storageTS = LocalDateTime.now())
    val newConfigDir = createConfigDir(version = "", modern = true, product = "newConfig")

    // NB: currently, it works even without the property because of the `PluginManagerCore.isRunningFromSources()` condition
    withSystemProperty<RuntimeException>(PathManager.PROPERTY_CONFIG_PATH, newConfigDir.toString()) {
      ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
    }

    assertThat(newConfigDir).isEmptyDirectory
  }

  @Test fun `skipping automatic import when running from sources`() {
    assumeTrue(PluginManagerCore.isRunningFromSources())

    val oldConfigDir = createConfigDir("2025.2", storageTS = LocalDateTime.now())
    val newConfigDir = createConfigDir(version = "2025.3")
    assertThat(findConfigDirectories(newConfigDir).paths).containsExactly(oldConfigDir)

    withSystemProperty<RuntimeException>(PathManager.PROPERTY_CONFIG_PATH, null) {
      ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
    }

    assertThat(newConfigDir).isEmptyDirectory
  }

  @Test fun `explicit settings import dialog`() {
    val oldConfigDir = createConfigDir("2025.2", storageTS = LocalDateTime.now())
    val newConfigDir = createConfigDir(version = "2025.3")
    assertThat(findConfigDirectories(newConfigDir).paths).containsExactly(oldConfigDir)

    assertThatThrownBy {
      withSystemProperty<RuntimeException>("intellij.startup.wizard", "false") {
        withSystemProperty<RuntimeException>("idea.initially.ask.config", "true") {
          ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
        }
      }
    }.isInstanceOf(UnsupportedOperationException::class.java).hasMessage("Unit test mode")
  }

  @Test fun `settings import dialog when no candidates`() {
    val newConfigDir = createConfigDir(version = "2025.3")

    assertThatThrownBy {
      withSystemProperty<RuntimeException>("intellij.startup.wizard", "false") {
        ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
      }
    }.isInstanceOf(UnsupportedOperationException::class.java).hasMessage("Unit test mode")
  }

  @Test fun `skipping automatic import from an old directory`() {
    createConfigDir("2024.2", storageTS = LocalDateTime.now().minusYears(1))
    val newConfigDir = createConfigDir(version = "2025.3")

    assertThatThrownBy {
      withSystemProperty<RuntimeException>("intellij.startup.wizard", "false") {
        ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
      }
    }.isInstanceOf(UnsupportedOperationException::class.java).hasMessage("Unit test mode")
  }

  @Test fun `avoiding settings import dialog on the very first start`() {
    val newConfigDir = createConfigDir(version = "2025.3")

    withSystemProperty<RuntimeException>("intellij.startup.wizard", "false") {
      ConfigImportHelper.importConfigsTo(true, newConfigDir, emptyList(), LOG)
    }

    assertThat(newConfigDir).isEmptyDirectory
  }

  @Test fun `avoiding settings import dialog when suppressed`() {
    val newConfigDir = createConfigDir(version = "2025.3")

    withSystemProperty<RuntimeException>("intellij.startup.wizard", "false") {
      withSystemProperty<RuntimeException>("idea.initially.ask.config", "never") {
        ConfigImportHelper.importConfigsTo(false, newConfigDir, emptyList(), LOG)
      }
    }

    assertThat(newConfigDir).isEmptyDirectory
  }

  @Test fun `uses broken plugins from marketplace by default`() {
    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()
    plugin("my-plugin") { dependsIntellijModulesLang(); version = "1.0" }.buildMainJar(oldPluginsDir.resolve("my-plugin.jar"))
    plugin("my-plugin-2") { dependsIntellijModulesLang(); version = "1.0" }.buildMainJar(oldPluginsDir.resolve("my-plugin-2.jar"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    val brokenPluginsDownloaded = AtomicInteger()
    val server = createTestServer(testRootDisposable)
    server.createContext("/files/brokenPlugins.json") { handler ->
      brokenPluginsDownloaded.incrementAndGet()
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write("""
          [{"id": "my-plugin", "version": "1.0", "since": "999.0", "until": "999.0", "originalSince": "1.0", "originalUntil": "999.9999"},
          {"id": "my-plugin-2", "version": "1.0", "since": "200.0", "until": "203.0", "originalSince": "1.0", "originalUntil": "999.9999"}]
        """.trimIndent())
      }
    }
    ApplicationManager.getApplication().replaceService(MarketplaceCustomizationService::class.java, object : MarketplaceCustomizationService {
      override fun getPluginManagerUrl(): String = server.url
      override fun getPluginDownloadUrl(): String = server.url + "/404"
      override fun getPluginsListUrl(): String  = throw AssertionError("unexpected")
      override fun getPluginHomepageUrl(pluginId: PluginId): String  = throw AssertionError("unexpected")
    }, testRootDisposable)

    configImportMarketplaceStub.unset() // enable marketplace fetching
    val options = ConfigImportOptions(LOG).apply {
      headless = true
      compatibleBuildNumber = BuildNumber.fromString("201.1")
    }
    doImport(oldConfigDir, newConfigDir, options)

    assertThat(brokenPluginsDownloaded).hasValue(1)
    assertThat(newPluginsDir).exists()
      .isDirectoryContaining { it.name == "my-plugin-2.jar" }
      .isDirectoryNotContaining { it.name == "my-plugin.jar" }
  }

  @Test fun `update only incompatible plugins`() = updatePlugins(updateIncompatibleOnly = true)

  @Test fun `update all plugins`() = updatePlugins(updateIncompatibleOnly = false)

  private fun updatePlugins(updateIncompatibleOnly: Boolean) {
    // com.intellij.openapi.application.ConfigImportHelper.UPDATE_INCOMPATIBLE_PLUGINS_PROPERTY
    setSystemProperty("idea.config.import.update.incompatible.plugins.only", updateIncompatibleOnly.toString(), testRootDisposable)

    val oldConfigDir = newTempDir("oldConfig")
    val oldPluginsDir = oldConfigDir.resolve("plugins").createDirectories()

    fun spec(id: String, version: String) = plugin(id) { dependsIntellijModulesLang(); this@plugin.version = version }
    spec("broken", "1.0").buildMainJar(oldPluginsDir.resolve("broken.jar"))
    spec("update", "1.0").buildMainJar(oldPluginsDir.resolve("update.jar"))
    spec("migrate", "1.0").buildMainJar(oldPluginsDir.resolve("migrate.jar"))
    spec("disabled", "1.0").buildMainJar(oldPluginsDir.resolve("disabled.jar"))

    val repoDir = newTempDir("repo")
    spec("broken", "1.1").buildMainJar(repoDir.resolve("broken.jar"))
    spec("update", "1.1").buildMainJar(repoDir.resolve("update.jar"))
    spec("disabled", "1.1").buildMainJar(repoDir.resolve("disabled.jar"))

    saveDisabledPluginsAndInvalidate(oldConfigDir, listOf("disabled"))

    val newConfigDir = newTempDir("newConfig")
    val newPluginsDir = newConfigDir.resolve("plugins")

    val server = createTestServer(testRootDisposable)
    server.createContext("/files/brokenPlugins.json") { handler ->
      handler.sendResponseHeaders(200, 0)
      handler.responseBody.writer().use {
        it.write("""
          [{"id": "broken", "version": "1.0", "since": "999.0", "until": "999.0", "originalSince": "1.0", "originalUntil": "999.9999"},
          {"id": "broken", "version": "1.1", "since": "999.0", "until": "999.0", "originalSince": "1.0", "originalUntil": "999.9999"}]
        """.trimIndent())
      }
    }
    server.createContext("/download") { handler ->
      val id = handler.requestURI.queryParameters["id"]!!
      if (id == "broken") {
        handler.sendResponseHeaders(404, -1) // incompatible
        return@createContext
      }
      val content = repoDir.resolve("${id}.jar").readBytes()
      handler.responseHeaders.add("Content-Disposition", "attachment; filename=${id}.jar")
      handler.sendResponseHeaders(200, content.size.toLong())
      handler.responseBody.use {
        it.write(content)
      }
    }
    ApplicationManager.getApplication().replaceService(
      MarketplaceCustomizationService::class.java,
      object : MarketplaceCustomizationService {
        override fun getPluginManagerUrl(): String = server.url
        override fun getPluginDownloadUrl(): String = server.url.trimEnd('/') + "/download"
        override fun getPluginsListUrl(): String = throw AssertionError("unexpected")
        override fun getPluginHomepageUrl(pluginId: PluginId): String = throw AssertionError("unexpected")
      },
      testRootDisposable
    )

    configImportMarketplaceStub.unset() // enable marketplace fetching
    val options = ConfigImportOptions(LOG).apply {
      headless = true
      compatibleBuildNumber = BuildNumber.fromString("201.1")
    }
    ConfigImportHelper.testLastCompatiblePluginUpdatesFetcher = Function {
      buildMap {
        for (id in listOf("update", "disabled", "migrate")) {
          val pid = PluginId.getId(id)
          val node = PluginNode(pid).apply { version = if (id == "migrate") "1.0" else "1.1" }
          put(pid, node)
        }
      }
    }
    doImport(oldConfigDir, newConfigDir, options)

    assertThat(newPluginsDir).exists()
    assertThat(newPluginsDir.listDirectoryEntries())
      .containsExactlyInAnyOrder(newPluginsDir.resolve("update.jar"), newPluginsDir.resolve("migrate.jar"), newPluginsDir.resolve("disabled.jar"))
    assertThat(newPluginsDir.resolve("update.jar")).hasSameBinaryContentAs((if (updateIncompatibleOnly) oldPluginsDir else repoDir).resolve("update.jar"))
    assertThat(newPluginsDir.resolve("migrate.jar")).hasSameBinaryContentAs(oldPluginsDir.resolve("migrate.jar"))
    assertThat(newPluginsDir.resolve("disabled.jar")).hasSameBinaryContentAs((if (updateIncompatibleOnly) oldPluginsDir else repoDir).resolve("disabled.jar"))
  }

  private fun createTestServer(disposable: Disposable): HttpServer {
    val server = HttpServer.create()!!
    server.bind(InetSocketAddress(0), 1)
    server.start()
    disposable.whenDisposed { server.stop(0) }
    return server
  }
}
