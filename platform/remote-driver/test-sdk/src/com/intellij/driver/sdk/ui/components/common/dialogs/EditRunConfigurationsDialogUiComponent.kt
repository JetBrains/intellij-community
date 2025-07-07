package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.PopupUiComponent
import com.intellij.driver.sdk.ui.components.elements.popup
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.xQuery
import javax.swing.JDialog
import javax.swing.JWindow
import kotlin.time.Duration.Companion.seconds

fun IdeaFrameUI.editRunConfigurationsDialog(action: EditRunConfigurationsDialogUiComponent.() -> Unit): EditRunConfigurationsDialogUiComponent =
  x(EditRunConfigurationsDialogUiComponent::class.java) { and(byType(JDialog::class.java), byAccessibleName("Run/Debug Configurations")) }
    .apply(action)

class EditRunConfigurationsDialogUiComponent(data: ComponentData) : DialogUiComponent(data) {
  val addNewConfigurationButton = x { byAccessibleName("Add New Configuration") }
  val runButton = x { and(byType("com.intellij.ui.components.BasicOptionButtonUI${"$"}MainButton"), byAccessibleName("Run")) }

  fun addNewRunConfigurationPopup(block: PopupUiComponent.() -> Unit = {}): PopupUiComponent =
    popup(xQuery { componentWithChild(byType(JWindow::class.java), byAccessibleName("Add New Configuration")) }).apply(block)

  fun addNewRunConfiguration(type: String, name: String) {
    addNewConfigurationButton.click()
    keyboard {
      typeText(type, 0)
      enter()
    }

    textField { and(byClass("JTextField"), byAccessibleName("Name:")) }.waitFound(10.seconds).text = name
  }
}