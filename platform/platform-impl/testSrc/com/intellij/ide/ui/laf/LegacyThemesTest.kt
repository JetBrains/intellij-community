// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf

import com.intellij.ide.ui.ThemeListProvider
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ExperimentalUI
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private val legacyThemeIds = listOf("ExperimentalDark", "ExperimentalLight", "ExperimentalLightWithLightHeader", "Darcula")
private val islandsThemeIds = listOf("Islands Dark", "Islands Light", "Islands Darcula", "JetBrainsHighContrastTheme")

@TestApplication
internal class LegacyThemesTest {
  private val shownThemeIds: List<String>
    get() = ThemeListProvider.getInstance().getShownThemes().items.map { it.id }

  private val isShowKeyOn: Boolean
    get() = RegistryManager.getInstance().`is`(LegacyThemes.SHOW_LEGACY_THEMES_KEY)

  @BeforeEach
  fun checkNewUi() {
    assertThat(ExperimentalUI.isNewUI()).describedAs("the test needs the new UI").isTrue()
  }

  @Test
  @RegistryKey(key = LegacyThemes.SHOW_LEGACY_THEMES_KEY, value = "false")
  fun `the theme lists hide the legacy themes and keep the Islands themes`() {
    assertThat(shownThemeIds).containsAll(islandsThemeIds).doesNotContainAnyElementsOf(legacyThemeIds)
  }

  @Test
  @RegistryKey(key = LegacyThemes.SHOW_LEGACY_THEMES_KEY, value = "true")
  fun `the registry key shows the legacy themes`() {
    assertThat(shownThemeIds).containsAll(islandsThemeIds).containsAll(legacyThemeIds)
  }

  @Test
  @RegistryKey(key = LegacyThemes.SHOW_LEGACY_THEMES_KEY, value = "false")
  fun `a legacy theme turns on the registry key`() {
    LegacyThemes.showIfUsed(listOf("Islands Light", "ExperimentalDark"))

    assertThat(isShowKeyOn).isTrue()
  }

  @Test
  @RegistryKey(key = LegacyThemes.SHOW_LEGACY_THEMES_KEY, value = "false")
  fun `the Islands themes keep the registry key off`() {
    LegacyThemes.showIfUsed(islandsThemeIds)

    assertThat(isShowKeyOn).isFalse()
  }
}
