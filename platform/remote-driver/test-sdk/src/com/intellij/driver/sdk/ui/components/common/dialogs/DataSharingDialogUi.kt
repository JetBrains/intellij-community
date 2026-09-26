package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.ui

fun Driver.dataSharingDialog(action: DataSharingDialogUi.() -> Unit) =
  ui.x(DataSharingDialogUi::class.java) { byTitle("Data Sharing") }.apply(action)

class DataSharingDialogUi(data: ComponentData): UiComponent(data) {
  val acceptButton get() = x { byAccessibleName("Send Anonymous Statistics") }
  val declineButton get() = x { byAccessibleName("Don't Send") }
}