package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import kotlin.time.Duration.Companion.seconds

fun Finder.dialog(@Language("xpath") xpath: String? = null, title: String? = null, action: DialogUiComponent.() -> Unit = {}): DialogUiComponent {
  val dialogXpath = when {
    xpath != null -> xpath
    title != null -> "//div[@title='$title']"
    else -> "//div[@class='MyDialog']"
  }
  return x(dialogXpath, DialogUiComponent::class.java).apply(action)
}

fun Finder.dialog(locator: QueryBuilder.() -> String, action: DialogUiComponent.() -> Unit = {}): DialogUiComponent {
  return x(DialogUiComponent::class.java) { locator() }.apply(action)
}

fun Finder.isDialogOpened(@Language("xpath") xpath: String? = null) =
  xx(xpath ?: "//div[@class='MyDialog']", DialogUiComponent::class.java).list().isNotEmpty()

fun Finder.dialog(@Language("xpath") xpath: String? = null, action: DialogUiComponent.() -> Unit) =
  x(xpath ?: "//div[@class='MyDialog']", DialogUiComponent::class.java).action()

fun Finder.waitForNoOpenedDialogs() {
  waitFor(message = "Dialog is closed", timeout = 100.seconds) {
    !isDialogOpened()
  }
}

fun WelcomeScreenUI.aboutDialog(action: AboutDialogUi.() -> Unit) = x(AboutDialogUi::class.java) { contains(byTitle("About")) }.apply(action)

class AboutDialogUi(data: ComponentData) : UiComponent(data) {
  val closeButton: UiComponent
    get() = x { byAccessibleName("Close") }
}

class DialogUiComponent(data: ComponentData) : UiComponent(data) {

  fun pressButton(text: String) = x("//div[@class='JButton' and @visible_text='$text']").click()
}