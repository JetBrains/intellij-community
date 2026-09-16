// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.laf

import com.intellij.ide.ui.UITheme
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfoImpl
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Locks the theme ancestry that [UITheme.isBasedOnTheme] reports.
 *
 * The icon palette scope and the "Update available" toolbar widget both branch on this ancestry.
 */
@TestApplication
class UIThemeAncestryTest {

  @Test
  fun `a theme is based on itself`() {
    assertBasedOn(UITheme.EXPERIMENTAL_LIGHT_ID, UITheme.EXPERIMENTAL_LIGHT_ID)
    assertBasedOn(UITheme.EXPERIMENTAL_DARK_ID, UITheme.EXPERIMENTAL_DARK_ID)
  }

  @Test
  fun `a direct child is based on its parent`() {
    assertBasedOn(UITheme.EXPERIMENTAL_LIGHT_WITH_LIGHT_HEADER_ID, UITheme.EXPERIMENTAL_LIGHT_ID)
    assertBasedOn("Islands Dark", UITheme.EXPERIMENTAL_DARK_ID)
  }

  @Test
  fun `a grandchild is based on its grandparent`() {
    // Islands Light -> ExperimentalLightWithLightHeader -> ExperimentalLight
    assertBasedOn(UITheme.ISLANDS_LIGHT_ID, UITheme.EXPERIMENTAL_LIGHT_WITH_LIGHT_HEADER_ID)
    assertBasedOn(UITheme.ISLANDS_LIGHT_ID, UITheme.EXPERIMENTAL_LIGHT_ID)
  }

  @Test
  fun `an unrelated theme is not based on the experimental themes`() {
    // "Darcula" has no parent at all, "IntelliJ Light" descends from "IntelliJ" and "Darcula"
    for (themeId in listOf("Darcula", "JetBrainsLightTheme")) {
      assertNotBasedOn(themeId, UITheme.EXPERIMENTAL_LIGHT_ID)
      assertNotBasedOn(themeId, UITheme.EXPERIMENTAL_DARK_ID)
    }
  }

  @Test
  fun `a light theme is not based on a dark one`() {
    assertNotBasedOn(UITheme.EXPERIMENTAL_LIGHT_ID, UITheme.EXPERIMENTAL_DARK_ID)
    assertNotBasedOn(UITheme.ISLANDS_LIGHT_ID, UITheme.EXPERIMENTAL_DARK_ID)
    assertNotBasedOn(UITheme.EXPERIMENTAL_DARK_ID, UITheme.EXPERIMENTAL_LIGHT_ID)
  }

  @Test
  fun `a parent is not based on its child`() {
    assertNotBasedOn(UITheme.EXPERIMENTAL_LIGHT_ID, UITheme.EXPERIMENTAL_LIGHT_WITH_LIGHT_HEADER_ID)
    assertNotBasedOn(UITheme.EXPERIMENTAL_LIGHT_ID, UITheme.ISLANDS_LIGHT_ID)
  }

  private fun assertBasedOn(themeId: String, baseThemeId: String) {
    assertTrue(UITheme.isBasedOnTheme(findTheme(themeId), baseThemeId), "'$themeId' must be based on '$baseThemeId'")
  }

  private fun assertNotBasedOn(themeId: String, baseThemeId: String) {
    assertFalse(UITheme.isBasedOnTheme(findTheme(themeId), baseThemeId), "'$themeId' must not be based on '$baseThemeId'")
  }

  private fun findTheme(themeId: String): UITheme {
    val info = UiThemeProviderListManager.getInstance().findThemeById(themeId)
    requireNotNull(info as? UIThemeLookAndFeelInfoImpl) { "Cannot find the theme '$themeId'" }
    return info.theme
  }
}
