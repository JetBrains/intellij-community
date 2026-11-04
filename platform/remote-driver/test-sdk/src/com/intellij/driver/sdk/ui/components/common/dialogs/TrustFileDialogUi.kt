package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.JButtonUiComponent
import com.intellij.driver.sdk.ui.components.elements.JCheckBoxUi
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.ui

/**
 * The 'Trust File?' dialog, see `com.intellij.ide.trustedProjects.impl.TrustedFileDialog`.
 * The window has no title, so the lookup matches the button only this dialog has.
 */
fun Driver.trustFileDialog(action: TrustFileDialogUi.() -> Unit = {}): TrustFileDialogUi =
  ui.x(TrustFileDialogUi::class.java) {
    componentWithChild(byType("javax.swing.JDialog"), byAccessibleName("Stay in Safe Mode"))
  }.apply(action)

class TrustFileDialogUi(data: ComponentData) : UiComponent(data) {
  val trustFileButton: JButtonUiComponent = button { byAccessibleName("Trust File") }
  val stayInSafeModeButton: JButtonUiComponent = button { byAccessibleName("Stay in Safe Mode") }

  val trustFolderCheckBox: JCheckBoxUi =
    checkBox("'Trust all files in the folder' checkbox") { contains(byAccessibleName("Trust all files in")) }

  /** The trust button gets this text when [trustFolderCheckBox] is selected. A [folderName] longer than 18 characters is shortened. */
  fun trustFolderButton(folderName: String): JButtonUiComponent = button { byAccessibleName("Trust '$folderName' Folder") }
}
