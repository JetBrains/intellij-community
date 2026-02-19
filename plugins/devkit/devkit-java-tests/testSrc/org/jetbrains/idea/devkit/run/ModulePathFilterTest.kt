// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.run

import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class ModulePathFilterTest {
  companion object {
    private val projectFixture = projectFixture()
    private val moduleFixture = projectFixture.moduleFixture("src")
  }

  private val project get() = projectFixture.get()
  private val module get() = moduleFixture.get()

  // Registry key tests
  @Test
  fun testRegistryKeyExists() {
    val value = Registry.`is`("devkit.module.hyperlink.show.chooser", false)
    assertThat(value).isFalse()
  }

  @Test
  fun testRegistryKeyCanBeChanged() {
    val registryValue = Registry.get("devkit.module.hyperlink.show.chooser")
    val originalValue = registryValue.asBoolean()
    try {
      registryValue.setValue(true)
      assertThat(Registry.`is`("devkit.module.hyperlink.show.chooser", false)).isTrue()

      registryValue.setValue(false)
      assertThat(Registry.`is`("devkit.module.hyperlink.show.chooser", false)).isFalse()
    }
    finally {
      registryValue.setValue(originalValue)
    }
  }

  @Test
  @RegistryKey(key = "devkit.module.hyperlink.show.chooser", value = "false")
  fun testHyperlinkInfoWithRegistryDisabled() {
    val hyperlinkInfo = ModuleFilesHyperlinkInfo(module)
    assertThat(hyperlinkInfo).isNotNull
  }

  @Test
  @RegistryKey(key = "devkit.module.hyperlink.show.chooser", value = "true")
  fun testHyperlinkInfoWithRegistryEnabled() {
    val hyperlinkInfo = ModuleFilesHyperlinkInfo(module)
    assertThat(hyperlinkInfo).isNotNull
  }

  // Pattern matching tests - test regex directly
  @Test
  fun testPatternMatchesIntellijModules() {
    assertThat(ModulePattern.matches("intellij.platform.core")).isTrue()
    assertThat(ModulePattern.matches("intellij.foo.bar.baz")).isTrue()
  }

  @Test
  fun testPatternMatchesKotlinModules() {
    assertThat(ModulePattern.matches("kotlin.base.plugin")).isTrue()
  }

  @Test
  fun testPatternMatchesFleetModules() {
    assertThat(ModulePattern.matches("fleet.rhizomedb.transactor")).isTrue()
  }

  @Test
  fun testPatternMatchesAndroidModules() {
    assertThat(ModulePattern.matches("android.tools.base")).isTrue()
  }

  @Test
  fun testPatternRequiresAtLeastTwoSegments() {
    assertThat(ModulePattern.matches("intellij")).isFalse()
    assertThat(ModulePattern.matches("kotlin")).isFalse()
  }

  @Test
  fun testPatternRejectsUnknownPrefixes() {
    assertThat(ModulePattern.matches("other.module.name")).isFalse()
    assertThat(ModulePattern.matches("com.example.plugin")).isFalse()
  }

  @Test
  fun testPatternFindsMultipleMatches() {
    val line = "intellij.foo.bar depends on kotlin.baz.qux"
    val matches = ModulePattern.findAll(line).toList()
    assertThat(matches).hasSize(2)
    assertThat(matches[0].value).isEqualTo("intellij.foo.bar")
    assertThat(matches[1].value).isEqualTo("kotlin.baz.qux")
  }

  // Filter tests - these don't need controlled module names
  @Test
  fun testPatternMatchesButModuleNotFound() {
    val filter = ModulePathFilter(project)
    val line = "Loading module intellij.nonexistent.module"
    val result = filter.applyFilter(line, line.length)
    assertThat(result).isNull()
  }

  @Test
  fun testNoMatchForNonModulePattern() {
    val filter = ModulePathFilter(project)
    val line = "Some random text without module names"
    val result = filter.applyFilter(line, line.length)
    assertThat(result).isNull()
  }

  @Test
  fun testNoMatchForSingleSegmentName() {
    val filter = ModulePathFilter(project)
    val line = "Loading module intellij"
    val result = filter.applyFilter(line, line.length)
    assertThat(result).isNull()
  }

  @Test
  fun testEntireLengthOffsetHandling() {
    val filter = ModulePathFilter(project)
    val line = "intellij.fake.module"
    val entireLength = 100
    val result = filter.applyFilter(line, entireLength)
    assertThat(result).isNull()
  }
}
