// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application

import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.SystemProperties
import com.intellij.util.system.OS
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.rules.ExternalResource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.function.Function

abstract class ConfigImportHelperBaseTest : BareTestFixtureTestCase() {
  @JvmField @Rule val memoryFs = InMemoryFsRule(OS.CURRENT)
  @JvmField @Rule val configImportMarketplaceStub = ConfigImportMarketplaceStub()

  @Before fun assertEnvironment() {
    assumeTrue(
      "'${ConfigImportHelper.IMPORT_FROM_ENV_VAR}' might affect tests. Please quit the IDE and open it again (don't use File | Restart)",
      System.getenv(ConfigImportHelper.IMPORT_FROM_ENV_VAR) == null
    )
  }

  protected fun newTempDir(name: String): Path =
    Files.createDirectories(memoryFs.fs.getPath("_temp", name).toAbsolutePath())

  protected fun createConfigDir(version: String, modern: Boolean = version >= "2020.1", product: String = "IntelliJIdea", storageTS: LocalDateTime? = null): Path {
    val path = when {
      modern -> PathManager.getDefaultConfigPathFor("${product}${version}")
      OS.CURRENT == OS.macOS -> "${SystemProperties.getUserHome()}/Library/Preferences/${product}${version}"
      else -> "${SystemProperties.getUserHome()}/.${product}${version}/config"
    }
    val dir = Files.createDirectories(memoryFs.fs.getPath(path).normalize())
    if (storageTS != null) writeStorageFile(dir, storageTS)
    return dir
  }

  protected fun writeStorageFile(configDir: Path, lastModified: LocalDateTime) {
    val file = configDir.resolve("${PathManager.OPTIONS_DIRECTORY}/${StoragePathMacros.NON_ROAMABLE_FILE}")
    Files.createDirectories(file.parent)
    Files.writeString(file, "<application/>")
    Files.setLastModifiedTime(file, FileTime.from(lastModified.toInstant(ZoneOffset.UTC)))
  }

  protected fun findInheritedDirectory(newConfigPath: Path, inheritedPath: String?): ConfigImportHelper.ConfigDirsSearchResult? =
    ConfigImportHelper.findInheritedDirectory(newConfigPath, inheritedPath, ConfigImportHelper.findCustomConfigImportSettings(), emptyList(), thisLogger())

  protected fun findConfigDirectories(newConfigPath: Path): ConfigImportHelper.ConfigDirsSearchResult =
    ConfigImportHelper.findConfigDirectories(newConfigPath, ConfigImportHelper.findCustomConfigImportSettings(), emptyList())

  // disables broken plugins fetcher from the Marketplace by default
  class ConfigImportMarketplaceStub : ExternalResource() {
    override fun before() {
      assert(ConfigImportHelper.testBrokenPluginsFetcherStub == null)
      ConfigImportHelper.testBrokenPluginsFetcherStub = Function { null } // using broken plugins from the distribution
      assert(ConfigImportHelper.testLastCompatiblePluginUpdatesFetcher == null)
      ConfigImportHelper.testLastCompatiblePluginUpdatesFetcher = Function { null }
    }

    fun unset() {
      ConfigImportHelper.testBrokenPluginsFetcherStub = null
      ConfigImportHelper.testLastCompatiblePluginUpdatesFetcher = null
    }

    override fun after() {
      unset()
    }
  }
}
