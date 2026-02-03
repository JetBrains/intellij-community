package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.ui

fun Finder.commercialReleaseDialog(action: CommercialReleaseUI.() -> Unit) {
  x("//div[@class='DialogRootPane']", CommercialReleaseUI::class.java).action()
}

fun Driver.commercialReleaseDialog(action: CommercialReleaseUI.() -> Unit) {
  this.ui.commercialReleaseDialog(action)
}

class CommercialReleaseUI(data: ComponentData) : UiComponent(data) {
  val choosePlan = button("Choose Plan…")
}
