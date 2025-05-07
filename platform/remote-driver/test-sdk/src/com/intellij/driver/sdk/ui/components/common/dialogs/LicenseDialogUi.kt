package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.radioButton
import com.intellij.driver.sdk.ui.ui

private fun Finder.licenseDialog(action: LicenseDialogUi.() -> Unit) {
  x("//div[@title='Manage Licenses']", LicenseDialogUi::class.java).action()
}

fun Driver.licenseDialog(action: LicenseDialogUi.() -> Unit = {}) = this.ui.licenseDialog(action)

class LicenseDialogUi(data: ComponentData) : UiComponent(data) {
  val licenseServerRadioButton = radioButton { byAccessibleName("License server") }
  val licenseServerTextField = x { byClass("JBTextField") }
  val activationCodeRadioButton = radioButton { byAccessibleName("Activation code") }
  val subscriptionRadioButton = radioButton { byAccessibleName("Subscription") }
  val activationCodeTextField = x("//div[contains(@classhierarchy, 'javax.swing.JTextArea')]")
  val activateButton = x("//div[@accessiblename='Activate' and @javaclass!='com.intellij.ui.dsl.builder.components.SegmentedButton']")
  val loginWithJbaButton = x { contains(byAccessibleName("Log In")) }
  val loginTroublesButton = x { or(contains(byAccessibleName("Troubles")), contains(byAccessibleName("Log in with token"))) }
  val startTrialTab = x { and(byClass("SegmentedButton"), byAccessibleName("Start trial")) }
  val startTrialButton = x("//div[@class!='SegmentedButton' and @accessiblename='Start Free 30-Day Trial']")
  val continueButton = x { byAccessibleName("Continue") }
  val removeLicenseButton: UiComponent = x { contains(byAccessibleName("Remove License")) }
  val closeButton = x { byAccessibleName("Close") }
  val optionsButton = x { byAccessibleName("Options") }

  //Depend on the free mode support (restart if IDE supports free mode)
  val exitButton: UiComponent = x { byAccessibleName("Quit ${driver.fullProductName}") }
  val restartButton: UiComponent = x { byAccessibleName("Restart Unsubscribed") }
}

val Finder.tokenAuthDialog get() = x("//div[@title='${driver.fullProductName}']", TokenAuthDialogUi::class.java)

fun Finder.tokenAuthDialog(action: TokenAuthDialogUi.() -> Unit) {
  tokenAuthDialog.action()
}

class TokenAuthDialogUi(data: ComponentData) : UiComponent(data) {
  val tokenField = x { and(byClass("JBTextField"), byVisibleText("IDE authorization token")) }
  val checkTokenButton = x { byVisibleText("Check Token") }
  val backButton = x { byText("← Back") }
  val copyLinkButton = x { contains(byVisibleText("copy the link")) }.getAllTexts { it.text.contains("copy the link") }[0]
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
  val discountLink get() = x { contains(byVisibleText("40%")) }.getAllTexts { it.text.contains("40%") }[0]
}

fun LicenseDialogUi.exitConfirmationDialog(action: ExitConfirmationDialogUi.() -> Unit) {
  x("//div[@title='Confirm Exit']", ExitConfirmationDialogUiCommonImpl::class.java).action()
}

fun LicenseDialogUi.restartToFreeModeConfirmationDialog(action: ExitConfirmationDialogUi.() -> Unit) {
  x("//div[@title='Confirm Restart']", RestartToFreeModeConfirmationDialogUi::class.java).action()
}

abstract class ExitConfirmationDialogUi(data: ComponentData): UiComponent(data) {
  abstract val exitConfirmButton: UiComponent
  abstract val cancelButton: UiComponent
}

class ExitConfirmationDialogUiCommonImpl(data: ComponentData): ExitConfirmationDialogUi(data) {
  override val exitConfirmButton: UiComponent = x { byAccessibleName("Exit") }
  override val cancelButton: UiComponent = x { byAccessibleName("Back to Activation") }
}

class RestartToFreeModeConfirmationDialogUi(data: ComponentData): ExitConfirmationDialogUi(data) {
  override val exitConfirmButton: UiComponent = x { byAccessibleName("Restart") }
  override val cancelButton: UiComponent = x { byAccessibleName("Back to Subscriptions") }
}