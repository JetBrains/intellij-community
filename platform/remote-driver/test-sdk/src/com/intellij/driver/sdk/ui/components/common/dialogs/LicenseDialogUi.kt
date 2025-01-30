package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.application.fullProductName
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.list
import com.intellij.driver.sdk.ui.components.elements.radioButton
import javax.swing.JList

fun Finder.licenseDialog(action: LicenseDialogUi.() -> Unit) {
  x("//div[@title='Manage Licenses']", LicenseDialogUi::class.java).action()
}

class LicenseDialogUi(data: ComponentData) : UiComponent(data) {
  val productsList = list { byType(JList::class.java) }
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
  val deactivateLicenseButton = x { contains(byAccessibleName("Deactivate License")) }
  val closeButton = x { byAccessibleName("Close") }
  val exitButton = x("//div[@accessiblename='Quit ${driver.fullProductName}']")
  val optionsButton = x { byAccessibleName("Options") }
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