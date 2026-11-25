package com.intellij.driver.sdk.license

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.button
import com.intellij.driver.sdk.ui.ui

fun Finder.trialDialog() = x(TrialDialogUi::class.java) {
  componentWithChild(byTitle(driver.fullProductName), byAccessibleName("Plans and Pricing"))
}

fun Finder.trialDialog(action: TrialDialogUi.() -> Unit) = trialDialog().action()

fun Driver.trialDialog(action: TrialDialogUi.() -> Unit = {}) = ui.trialDialog().apply(action)

class TrialDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val buyCommercialButton = x { byClass("LicenseDialogButton") and byAccessibleName("Buy for Commercial Use") }
  val extendTrialButton = x { byClass("LicenseDialogButton") and byAccessibleName("Extend Trial") }
  val nonCommercialLicenseButton = x { byClass("LicenseDialogButton") and byAccessibleName("Free for Learning and Hobby") }
  val pricingButton = x { byAccessibleName("Plans and Pricing") }
  val loginButton = x { contains(byAccessibleName("Log in")) }
  val activateLicenseButton = x { byAccessibleName("Activate License") }
  val openSourceButton = x { byAccessibleName("Open-source contributions without earning commercial benefits") }
  val agreeWithNonCommercialUseCheckBox = x { byAccessibleName("I agree to the non-commercial use ") }
  val startNonCommercialUseButton = button { byAccessibleName("Start Non-Commercial Use") }
}
