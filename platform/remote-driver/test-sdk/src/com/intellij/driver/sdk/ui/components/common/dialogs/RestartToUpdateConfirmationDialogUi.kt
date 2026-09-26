package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.ui

fun Finder.restartToUpdateConfirmationDialogUi(action: RestartToUpdateConfirmationDialogUi.() -> Unit = {}): RestartToUpdateConfirmationDialogUi = x(RestartToUpdateConfirmationDialogUi::class.java) {
  byTitle("${driver.fullProductName} and Plugin Updates")
}.apply(action)

fun Driver.restartToUpdateConfirmationDialogUi(action: RestartToUpdateConfirmationDialogUi.() -> Unit) {
  ui.restartToUpdateConfirmationDialogUi(action)
}

class RestartToUpdateConfirmationDialogUi(data: ComponentData) : DialogUiComponent(data) {
  override val primaryButtonText: String = "Restart"
  override val cancelButtonText: String = "Not Now"
}