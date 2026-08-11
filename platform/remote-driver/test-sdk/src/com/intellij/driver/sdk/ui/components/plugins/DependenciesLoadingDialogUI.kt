package com.intellij.driver.sdk.ui.components.plugins


import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.settings.SettingsDialogUiComponent
import javax.swing.JDialog

fun SettingsDialogUiComponent.dependenciesLoadingModalDialog(action: DependenciesLoadingDialogUI.() -> Unit = {}): DependenciesLoadingDialogUI =
  x(DependenciesLoadingDialogUI::class.java, { componentWithChild(byType(JDialog::class.java), byAccessibleName("Checking Plugin Dependencies")) })
    .apply(action)

open class DependenciesLoadingDialogUI(data: ComponentData) : DialogUiComponent(data)