package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import javax.swing.JDialog
import kotlin.jvm.java

fun IdeaFrameUI.editRunConfigurationsDialog(action: EditRunConfigurationsDialogUiComponent.() -> Unit): EditRunConfigurationsDialogUiComponent =
  x(EditRunConfigurationsDialogUiComponent::class.java) { and(byType(JDialog::class.java), byAccessibleName("Run/Debug Configurations")) }
    .apply(action)

class EditRunConfigurationsDialogUiComponent(data: ComponentData): DialogUiComponent(data) {
  val addNewConfigurationButton = x { byAccessibleName("Add New Configuration") }
}