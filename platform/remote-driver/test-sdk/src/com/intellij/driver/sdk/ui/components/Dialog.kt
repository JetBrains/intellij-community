package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators
import org.intellij.lang.annotations.Language

fun Finder.dialog(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyDialog']", UiComponent::class.java)

fun WelcomeScreenUI.aboutDialog(action: AboutDialogUi.() -> Unit) = x(Locators.byTitleContains("About"), AboutDialogUi::class.java).apply(action)

class AboutDialogUi(data: ComponentData): UiComponent(data) {
  val closeButton: UiComponent
    get() = x(Locators.byAccessibleName("Close"))
}