package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.radioButton
import com.intellij.driver.sdk.ui.ui

private fun Finder.licenseDialog(action: LicenseDialogUi.() -> Unit) {
  x(LicenseDialogUi::class.java) { or(byTitle("Manage Licenses"), byTitle("Manage Subscriptions")) }.action()
}

fun Driver.licenseDialog(action: LicenseDialogUi.() -> Unit = {}) = this.ui.licenseDialog(action)

class LicenseDialogUi(data: ComponentData) : UiComponent(data) {
  val licenseServerRadioButton = radioButton { byAccessibleName("License server") }
  val licenseServerTextField = x { byClass("JBTextField") }
  val activationCodeRadioButton = radioButton { byAccessibleName("Activation code") }
  val subscriptionRadioButton = radioButton { or(byAccessibleName("Subscription"), byAccessibleName("JetBrains Account")) }
  val activationCodeTextField = x("//div[contains(@classhierarchy, 'javax.swing.JTextArea')]")
  val activateButton = x("//div[@accessiblename='Activate' and @javaclass!='com.intellij.ui.dsl.builder.components.SegmentedButton']")
  val activateAnotherLicenseButton = x { or(contains(byAccessibleName("Activate Another License")), contains(byAccessibleName("Activate Another Subscription"))) }
  val loginWithJbaButton = x { contains(byAccessibleName("Log In")) }
  val loginTroublesButton = x { or(contains(byAccessibleName("Troubles")), contains(byAccessibleName("Log in with token"))) }
  val startTrialTab = x { and(byClass("SegmentedButton"), byAccessibleName("Start trial")) }
  val startTrialButton = x("//div[@class!='SegmentedButton' and @accessiblename='Start Free 30-Day Trial']")
  val continueButton = x { byAccessibleName("Continue") }
  val removeLicenseButton: UiComponent = x { or(contains(byAccessibleName("Remove License")), contains(byAccessibleName("Deactivate Subscription"))) }
  val closeButton = x { byAccessibleName("Close") and byClass("JButton") }
  val optionsButton = x { byAccessibleName("Options") }
  val closeErrorBannerButton = x { byAccessibleName("Close") and contains(byClass("InplaceButton")) }

  val exitButton: UiComponent = x { byAccessibleName("Quit ${driver.fullProductName}") }
}

fun Finder.authDialog(action: AuthDialogUi.() -> Unit) {
  x(AuthDialogUi::class.java) { byTitle("Log in to ${driver.fullProductName}") }.action()
}

fun Driver.authDialog(action: AuthDialogUi.() -> Unit = {}) = this.ui.authDialog(action)

class AuthDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val tokenField = x { and(byClass("JBTextField"), byVisibleText("IDE authorization token")) }
  val checkTokenButton = x { byVisibleText("Check Token") }
  val backButton = x { byText("← Back") }
  val loginToJBAButton = x { byVisibleText("Log in to JetBrains Account") }
  val getStartedButton = x { byVisibleText("Get Started") }
  val copyLinkButton get() = x { contains(byVisibleText("copy the link")) }.getAllTexts().single { it.text.contains("copy the link") }
}


val Finder.educationalLicenseExpirationDialog
  get() = x(EducationalLicenseExpirationDialogUi::class.java) {
    componentWithChild(byClass("MyDialog"), contains(byVisibleText("Your license expires in")))
  }

fun Finder.educationalLicenseExpirationDialog(action: EducationalLicenseExpirationDialogUi.() -> Unit) {
  educationalLicenseExpirationDialog.action()
}

class EducationalLicenseExpirationDialogUi(data: ComponentData) : UiComponent(data) {
  val renewLicenseButton = x { contains(byText("Renew license")) }
  val dismissButton = x { byText("Dismiss") }
  val discountLink get() = x { contains(byVisibleText("40%")) }.getAllTexts().single { it.text.contains("40%") }
}

fun LicenseDialogUi.exitConfirmationDialog(action: ExitConfirmationDialogUi.() -> Unit) {
  x(ExitConfirmationDialogUi::class.java) { byTitle("Confirm Exit") }.action()
}

class ExitConfirmationDialogUi(data: ComponentData): DialogUiComponent(data) {
  val exitConfirmButton: UiComponent = x { byAccessibleName("Exit") }
  val backToActivationButton: UiComponent = x { byAccessibleName("Back to Activation") }
}

fun LicenseDialogUi.removeLicenseConfirmationDialog(action: RemoveLicenseConfirmationDialogUi.() -> Unit) {
  x(RemoveLicenseConfirmationDialogUi::class.java) { byTitle("Remove License") or byTitle("Deactivate Subscription") }.action()
}

class RemoveLicenseConfirmationDialogUi(data: ComponentData): DialogUiComponent(data) {
  val confirmButton: UiComponent = x { (byAccessibleName("Remove License") or byAccessibleName("Deactivate and Restart")) and byClass("JButton") }
  val cancelRemoveButton: UiComponent = x { (byAccessibleName("TODO") or byAccessibleName("Keep Subscription")) and byClass("JButton") }
}