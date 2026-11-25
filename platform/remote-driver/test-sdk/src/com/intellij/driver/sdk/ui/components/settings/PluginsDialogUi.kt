package com.intellij.driver.sdk.ui.components.settings

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.ui
import javax.swing.JDialog
import javax.swing.JFrame

fun Finder.pluginsDialog(action: SettingsDialogUiComponent.() -> Unit = {}): SettingsDialogUiComponent =
  driver.ui.x(SettingsDialogUiComponent::class.java) {
    componentWithChild(
      and(
        or(byType(JDialog::class.java), byType(JFrame::class.java)),
        or(byAccessibleName("Settings"), byAccessibleName("Plugins")),
      ),
      byAccessibleName("Search plugins"),
    )
  }.apply(action)
