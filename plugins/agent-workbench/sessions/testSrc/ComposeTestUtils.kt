// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.DisabledAppearanceValues
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.GlobalMetrics
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.foundation.theme.ThemeColorPalette
import org.jetbrains.jewel.foundation.theme.ThemeDefinition
import org.jetbrains.jewel.foundation.theme.ThemeIconData
import org.jetbrains.jewel.intui.standalone.theme.default
import org.jetbrains.jewel.ui.ComponentStyling
import org.jetbrains.jewel.ui.LocalMenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.LocalMenuItemShortcutProvider
import org.jetbrains.jewel.ui.LocalTypography
import org.jetbrains.jewel.ui.MenuItemShortcutHintProvider
import org.jetbrains.jewel.ui.MenuItemShortcutProvider
import org.jetbrains.jewel.ui.Typography
import org.jetbrains.jewel.ui.component.ContextMenuItemOptionAction
import org.jetbrains.jewel.ui.icon.LocalNewUiChecker
import org.jetbrains.jewel.ui.icon.NewUiChecker
import org.jetbrains.jewel.ui.theme.BaseJewelTheme
import javax.swing.KeyStroke

internal fun ComposeContentTestRule.setContentWithTheme(content: @Composable () -> Unit) {
  setContent {
    BaseJewelTheme(createTestThemeDefinition(), ComponentStyling.default()) {
      CompositionLocalProvider(
        LocalTypography provides TestTypography,
        LocalNewUiChecker provides TestNewUiChecker,
        LocalMenuItemShortcutProvider provides EmptyMenuItemShortcutProvider,
        LocalMenuItemShortcutHintProvider provides EmptyMenuItemShortcutHintProvider,
      ) {
        content()
      }
    }
  }
}

private fun createTestThemeDefinition(): ThemeDefinition {
  return ThemeDefinition(
    name = "Test",
    isDark = false,
    globalColors =
      GlobalColors(
        borders = BorderColors(normal = Color.Black, focused = Color.Black, disabled = Color.Black),
        outlines =
          OutlineColors(
            focused = Color.Black,
            focusedWarning = Color.Black,
            focusedError = Color.Black,
            warning = Color.Black,
            error = Color.Black,
          ),
        text =
          TextColors(
            normal = Color.Black,
            selected = Color.Black,
            disabled = Color.Black,
            disabledSelected = Color.Black,
            info = Color.Black,
            error = Color.Black,
            warning = Color.Black,
          ),
        panelBackground = Color.White,
        toolwindowBackground = Color.White,
      ),
    globalMetrics = GlobalMetrics(outlineWidth = 10.dp, rowHeight = 24.dp),
    defaultTextStyle = TextStyle(fontSize = 13.sp),
    editorTextStyle = TextStyle(fontSize = 13.sp),
    consoleTextStyle = TextStyle(fontSize = 13.sp),
    contentColor = Color.Black,
    colorPalette = ThemeColorPalette.Empty,
    iconData = ThemeIconData.Empty,
    disabledAppearanceValues = DisabledAppearanceValues(brightness = 33, contrast = -35, alpha = 100),
  )
}

private object TestTypography : Typography {
  @get:Composable
  override val labelTextStyle: TextStyle
    get() = JewelTheme.defaultTextStyle

  @get:Composable
  override val labelTextSize
    get() = JewelTheme.defaultTextStyle.fontSize

  @get:Composable
  override val h0TextStyle: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val h1TextStyle: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val h2TextStyle: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val h3TextStyle: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val h4TextStyle: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val regular: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val medium: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val small: TextStyle
    get() = labelTextStyle

  @get:Composable
  override val editorTextStyle: TextStyle
    get() = JewelTheme.editorTextStyle

  @get:Composable
  override val consoleTextStyle: TextStyle
    get() = JewelTheme.consoleTextStyle
}

private object TestNewUiChecker : NewUiChecker {
  override fun isNewUi(): Boolean = true
}

private object EmptyMenuItemShortcutProvider : MenuItemShortcutProvider {
  override fun getShortcutKeyStroke(actionType: ContextMenuItemOptionAction): KeyStroke? = null
}

private object EmptyMenuItemShortcutHintProvider : MenuItemShortcutHintProvider {
  override fun getShortcutHint(actionType: ContextMenuItemOptionAction): String = ""
}
