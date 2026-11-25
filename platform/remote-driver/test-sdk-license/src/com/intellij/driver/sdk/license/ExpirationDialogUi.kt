@file:Suppress("PublicTestExtensionFunction")

package com.intellij.driver.sdk.license

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.components.elements.radioButton
import com.intellij.driver.sdk.ui.ui

private val Driver.subscriptionName
  get() = when (fullProductName.lowercase()) {
    "intellij idea" -> "Ultimate"
    "pycharm" -> "Pro"
    else -> error("Unknown product name $fullProductName, code: ${getProductVersion().productCode}")
  }

private val Driver.subscriptionNameWithAnArticle
  get() = when (fullProductName.lowercase()) {
    "intellij idea" -> "an Ultimate"
    "pycharm" -> "a Pro"
    else -> error("Unknown product name $fullProductName, code: ${getProductVersion().productCode}")
  }

fun Finder.expirationDialog() = x(ExpirationDialogUi::class.java) {
  componentWithChild(
    byClass("MyDialog"),
    contains(byVisibleText("Your ${driver.subscriptionName} subscription has ended"))
      or contains(byVisibleText("Your ${driver.subscriptionName} trial has ended")),
  )
}

fun Finder.expirationDialog(action: ExpirationDialogUi.() -> Unit = {}) = expirationDialog().action()

fun Driver.expirationDialog(action: ExpirationDialogUi.() -> Unit = {}) = ui.expirationDialog().apply(action)

class ExpirationDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val activateRadioButton = radioButton { byAccessibleName("Activate ${driver.subscriptionNameWithAnArticle} subscription") }
  val continueFreeRadioButton = radioButton { byAccessibleName("Continue for free without a subscription") }
  val activateButton = button { contains(byVisibleText("Activate ${driver.subscriptionName}")) and byClass("JButton") }
  val restartToFreeModeButton = button { contains(byVisibleText("Restart to Continue for Free")) and byClass("JButton") }
  val Driver.quitButton get() = x { byVisibleText("Quit $fullProductName") and byClass("LinkLabel") }
  val Driver.promoLink get() = x { byVisibleText("How ${driver.subscriptionName} subscription helps you") and byClass("BrowserLink") }
}