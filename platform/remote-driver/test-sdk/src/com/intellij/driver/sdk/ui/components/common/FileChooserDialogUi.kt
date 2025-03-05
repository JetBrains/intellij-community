package com.intellij.driver.sdk.ui.components.common

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.actionButton
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.wait
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.withRetries
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration.Companion.seconds

class FileChooserDialogUi(data: ComponentData) : DialogUiComponent(data) {
  val pathTextField = textField()
  val refreshActionButton = actionButton { byAccessibleName("Refresh") }
  val fileTree = tree()

  fun openPath(path: Path) {
    val absolutePath = path.toAbsolutePath().toString()
    wait(1.seconds)
    pathTextField.text = absolutePath
    withRetries(times = 3) {
      refreshActionButton.click()
      waitFor("$absolutePath is selected in file chooser tree", timeout = 3.seconds) {
        fileTree.collectSelectedPaths().singleOrNull()?.path?.last() == path.name
      }
      shouldBe {
        okButton.isEnabled()
      }
    }
    okButton.click()
  }
}