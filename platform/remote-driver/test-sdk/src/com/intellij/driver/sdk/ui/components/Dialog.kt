package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators
import org.intellij.lang.annotations.Language

fun Finder.dialog(@Language("xpath") xpath: String? = null) =
  x(xpath ?: "//div[@class='MyDialog']", DialogUiComponent::class.java)

fun WelcomeScreenUI.aboutDialog(action: AboutDialogUi.() -> Unit) = x(Locators.byTitleContains("About"), AboutDialogUi::class.java).apply(action)

class AboutDialogUi(data: ComponentData): UiComponent(data) {
  val closeButton: UiComponent
    get() = x(Locators.byAccessibleName("Close"))
}

class DialogUiComponent(data: ComponentData): UiComponent(data) {

  fun pressButton(text: String) = x("//div[@class='JButton' and @visible_text='$text']").click()
}