package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.UiRobot
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.FileChooserDialogUi
import com.intellij.driver.sdk.ui.components.common.WelcomeScreenUI
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import kotlin.time.Duration
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

fun WindowUiComponent.fileChooser(locator: QueryBuilder.() -> String, action: FileChooserDialogUi.() -> Unit = {}) = onFileChooserDialog(locator, action)

fun UiRobot.fileChooser(locator: QueryBuilder.() -> String, action: FileChooserDialogUi.() -> Unit = {}) = onFileChooserDialog(locator, action)

fun Finder.waitForNoOpenedDialogs(timeout: Duration = 100.seconds) {
  waitFor(message = "Dialog is closed", timeout) {
    !isDialogOpened()
  }
}

private fun Finder.onFileChooserDialog(locator: QueryBuilder.() -> String, action: FileChooserDialogUi.() -> Unit = {}): FileChooserDialogUi =
  x(FileChooserDialogUi::class.java) { locator() }.apply(action)

fun WelcomeScreenUI.aboutDialog(action: AboutDialogUi.() -> Unit) = x(AboutDialogUi::class.java) { contains(byTitle("About")) }.apply(action)

class AboutDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val closeButton: UiComponent
    get() = x { byAccessibleName("Close") }
}

open class DialogUiComponent(data: ComponentData) : WindowUiComponent(data) {
  protected open val primaryButtonText: String = "OK"
  protected open val cancelButtonText: String = "Cancel"

  val okButton: UiComponent
    get() = x { byAccessibleName(primaryButtonText) }
  val cancelButton: UiComponent
    get() = x { byAccessibleName(cancelButtonText) }

  fun pressButton(text: String) = x("//div[@class='JButton' and @visible_text='$text']").click()

  fun closeDialog() {
    super.dispose()
  }
}

