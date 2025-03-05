package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.JTreeUiComponent
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.ui.should
import javax.swing.JDialog
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.settingsDialog(action: SettingsDialogUiComponent.() -> Unit = {}): SettingsDialogUiComponent = onSettingsDialog(action = action)
fun WelcomeScreenUI.settingsDialog(action: SettingsDialogUiComponent.() -> Unit = {}): SettingsDialogUiComponent = onSettingsDialog(action = action)

private fun Finder.onSettingsDialog(
  locator: QueryBuilder.() -> String = { and(byType(JDialog::class.java), byAccessibleName("Settings")) },
  action: SettingsDialogUiComponent.() -> Unit,
): SettingsDialogUiComponent =
  x(SettingsDialogUiComponent::class.java, locator).apply(action)

open class SettingsDialogUiComponent(data: ComponentData) : DialogUiComponent(data) {
  open val settingsTree: JTreeUiComponent = accessibleTree { byAccessibleName("Settings categories") }

  fun openTreeSettingsSection(vararg path: String, fullMatch: Boolean = true) {
    settingsTree.should(message = "Settings tree is empty", timeout = 5.seconds) { collectExpandedPaths().isNotEmpty() }
    settingsTree.clickPath(*path, fullMatch = fullMatch)
  }

  fun content(action: UiComponent.() -> Unit): UiComponent =
    x { byType("com.intellij.openapi.options.ex.ConfigurableCardPanel") }.apply(action)
}