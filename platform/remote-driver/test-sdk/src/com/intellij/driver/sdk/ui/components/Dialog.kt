package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.remote.Window
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.withRetries
import org.intellij.lang.annotations.Language
import java.awt.Rectangle
import java.nio.file.Path
import kotlin.io.path.name
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

fun Finder.fileChooser(locator: QueryBuilder.() -> String, action: FileChooserDialogUi.() -> Unit = {}) =
  x(FileChooserDialogUi::class.java) { locator() }.apply(action)

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

open class DialogUiComponent(data: ComponentData) : UiComponent(data) {
  private val windowComponent by lazy {
    driver.cast(component, Window::class)
  }

  fun setBounds(bounds: Rectangle) = windowComponent.setBounds(bounds.x, bounds.y, bounds.width, bounds.height)

  fun pressButton(text: String) = x("//div[@class='JButton' and @visible_text='$text']").click()
}

class FileChooserDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val pathTextField = textField()
  val refreshActionButton = actionButton { byAccessibleName("Refresh") }
  val fileTree = tree()
  val okButton = x { byAccessibleName("OK") }

  fun openPath(path: Path) {
    val absolutePath = path.toAbsolutePath().toString()
    wait(1.seconds)
    pathTextField.text = absolutePath
    withRetries(times = 3) {
      refreshActionButton.click()
      waitFor("$absolutePath is selected in file chooser tree", timeout = 3.seconds) {
        fileTree.collectSelectedPaths().singleOrNull()?.path?.last() == path.name
      }
    }
    okButton.click()
  }
}