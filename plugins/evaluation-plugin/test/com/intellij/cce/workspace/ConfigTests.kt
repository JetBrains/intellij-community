package com.intellij.cce.workspace

import com.google.gson.Gson
import com.intellij.cce.actions.CompletionType
import com.intellij.cce.filter.impl.PackageRegexFilter
import com.intellij.cce.filter.impl.PackageRegexFilterConfiguration
import com.intellij.cce.filter.impl.StaticFilter
import com.intellij.cce.filter.impl.StaticFilterConfiguration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@DisplayName("Check config serialized and deserialized correctly")
class ConfigTests {
  companion object {
    val gson = Gson()
    private const val FILENAME = "config.json"
  }

  @TempDir
  lateinit var tempDir: Path

  @Test
  fun `default config`() = doTest {}

  @Test
  fun `modify field`() = doTest { emulateUser = true }

  @Test
  fun `with evaluation root`() = doTest {
    evaluationRoots.add(tempDir.resolve("project").resolve("subdir").toString())
  }

  @Test
  fun `with filters`() = doTest {
    filters[PackageRegexFilterConfiguration.id] = PackageRegexFilter("com\\.jetbrains\\..*")
    filters[StaticFilterConfiguration.id] = StaticFilter(false)
  }

  @Test
  fun `with comparison filters`() = doTest {
    mergeComparisonFilters(listOf(
      com.intellij.cce.workspace.filter.CompareSessionsFilter.create(
        com.intellij.cce.workspace.filter.CompareSessionsFilter.CompareFilterType.POSITION_BETTER.name, "BASIC BETTER", "BASIC")
    ))
  }

  @Test
  fun `update completion type`() = doTest {
    completionType = CompletionType.SMART
  }

  @Test
  fun `create default config if it's absent`() {
    val path = tempDir.resolve(FILENAME)
    assertThrows<IllegalArgumentException> { ConfigFactory.load(path) }
    assertConfigsTheSame("Default config should be created", ConfigFactory.defaultConfig(), ConfigFactory.load(path))
  }

  private fun doTest(init: Config.Builder.() -> Unit) {
    val before = Config.build(tempDir.resolve("project").toString(), "Java", init)
    ConfigFactory.save(before, tempDir, FILENAME)
    val after = ConfigFactory.load(tempDir.resolve(FILENAME))
    assertConfigsTheSame("Stored and restored configs should be equal", before, after)
  }

  @Test
  fun `use properties in project path`() {
    doTestPropertiesInConfig({ parametrized ->
                               Config.build(parametrized, "Java") {}
                             }, Config::projectPath)
  }

  @Test
  fun `use properties in output path`() {
    doTestPropertiesInConfig({ parametrized ->
                               Config.build("path-to-project", "Java") {
                                 outputDir = parametrized
                               }
                             }, Config::outputDir)
  }

  @Test
  fun `default session filters should be empty`() {
    val default = ConfigFactory.defaultConfig("project", "Kotlin")
    assertTrue { default.reports.sessionsFilters.isEmpty() }
    assertTrue { default.reports.comparisonFilters.isEmpty() }
  }

  private fun doTestPropertiesInConfig(factory: (String) -> Config, accessor: (Config) -> String) {
    val key = "MY_ENV"
    val value = "MY_TEST_VALUE"
    try {
      System.setProperty(key, value)
      val before = factory("foo/\${$key}/bar")
      ConfigFactory.save(before, tempDir, FILENAME)
      val after = ConfigFactory.load(tempDir.resolve(FILENAME))
      assertEquals("foo/$value/bar", accessor(after))
    }
    finally {
      System.clearProperty(key)
    }
  }

  private fun assertConfigsTheSame(message: String, expected: Config, actual: Config) {
    assertEquals(gson.toJson(expected), gson.toJson(actual), message)
  }
}