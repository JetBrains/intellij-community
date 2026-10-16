// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.projectStructureTests

import com.intellij.compose.ide.plugin.shared.TARGET_GRADLE_VERSION
import com.intellij.openapi.application.ex.PathManagerEx
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.inputStream
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.test.assertTrue

/**
 * Each Gradle project in the test data of the Compose plugin modules that keeps a Gradle wrapper must declare
 * [TARGET_GRADLE_VERSION] in it.
 *
 * `GradleImportingTestCase` runs with `DistributionType.DEFAULT_WRAPPED`, so the import reads the wrapper of the
 * project. `KotlinGradleImportingTestCase.configureByFiles` copies each test data file into the project, and it writes
 * the wrapper of the test data over the wrapper that the test framework generates. A different version makes the
 * test download another Gradle distribution.
 */
class ComposeTestDataGradleWrapperTest {

  @Test
  fun `collects some test data Gradle wrappers`() {
    assertTrue(testDataWrapperProperties().isNotEmpty())
  }

  @Test
  fun `test data Gradle wrappers declare the target Gradle version`() {
    val expectedDistributionUrl = "https://services.gradle.org/distributions/gradle-$TARGET_GRADLE_VERSION-bin.zip"
    val wrappersWithOtherDistribution = testDataWrapperProperties()
      .associateWith(::distributionUrl)
      .filterValues { it != expectedDistributionUrl }

    assertTrue(
      wrappersWithOtherDistribution.isEmpty(),
      "The following wrappers must declare $expectedDistributionUrl:\n" +
      wrappersWithOtherDistribution.entries.joinToString("\n") { (path, url) -> "$path: $url" },
    )
  }

  private fun testDataWrapperProperties(): List<Path> =
    composePluginRoot.listDirectoryEntries()
      .asSequence()
      .filter { it.name.startsWith(COMPOSE_PLUGIN_MODULE_NAME_PREFIX) }
      .map { it.resolve("testData") }
      .filter { it.isDirectory() }
      .flatMap { testDataRoot -> testDataRoot.walk().filter { it.endsWith(WRAPPER_PROPERTIES_RELATIVE_PATH) } }
      .sorted()
      .toList()

  private fun distributionUrl(wrapperProperties: Path): String? {
    val properties = Properties()
    wrapperProperties.inputStream().use(properties::load)
    return properties.getProperty("distributionUrl")
  }

  companion object {
    private val composePluginRoot: Path by lazy {
      Path.of(PathManagerEx.getCommunityHomePath(), "plugins/compose")
    }

    private const val COMPOSE_PLUGIN_MODULE_NAME_PREFIX: String = "intellij.compose.ide.plugin"

    private const val WRAPPER_PROPERTIES_RELATIVE_PATH: String = "gradle/wrapper/gradle-wrapper.properties"
  }
}
