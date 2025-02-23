package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.ui
import javax.swing.JCheckBox

fun Driver.agreementDialog(action: AgreementDialogUi.() -> Unit) =
  ui.x(AgreementDialogUi::class.java) { byTitle("$fullProductName User Agreement") }.apply(action)

class AgreementDialogUi(data: ComponentData): UiComponent(data) {
  val confirmCheckbox get() = x { byType(JCheckBox::class.java) }
  val continueButton get() = x { byAccessibleName("Continue") }
  val exitButton get() = x { byAccessibleName("Exit") }
}
