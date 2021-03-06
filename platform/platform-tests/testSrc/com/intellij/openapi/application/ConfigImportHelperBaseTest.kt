// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application

import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.SystemProperties
import org.junit.Rule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime

abstract class ConfigImportHelperBaseTest : BareTestFixtureTestCase() {
  @JvmField @Rule val memoryFs = InMemoryFsRule(SystemInfo.isWindows)
  @JvmField @Rule val localTempDir = TempDirectory()

  protected fun createConfigDir(version: String, modern: Boolean = version >= "2020.1", product: String = "IntelliJIdea", storageTS: Long = 0): Path {
    val path = when {
      modern -> PathManager.getDefaultConfigPathFor("${product}${version}")
      SystemInfo.isMac -> "${SystemProperties.getUserHome()}/Library/Preferences/${product}${version}"
      else -> "${SystemProperties.getUserHome()}/.${product}${version}/config"
    }
    val dir = Files.createDirectories(memoryFs.fs.getPath(path).normalize())
    if (storageTS > 0) writeStorageFile(dir, storageTS)
    return dir
  }

  protected fun writeStorageFile(config: Path, lastModified: Long) {
    val file = config.resolve("${PathManager.OPTIONS_DIRECTORY}/${StoragePathMacros.NON_ROAMABLE_FILE}")
    Files.createDirectories(file.parent)
    Files.write(file, "<application/>".toByteArray())
    Files.setLastModifiedTime(file, FileTime.fromMillis(lastModified))
  }

  protected fun findConfigDirectories(newConfigPath: Path): List<Path> = ConfigImportHelper.findConfigDirectories(newConfigPath).paths
}
