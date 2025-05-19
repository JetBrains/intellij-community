package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.client.Driver
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.elements.DialogUiComponent
import com.intellij.driver.sdk.ui.components.elements.checkBox
import com.intellij.driver.sdk.ui.components.elements.textField
import com.intellij.driver.sdk.ui.pasteText
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language

//fun Finder.newDockerConnectionDialog(@Language("xpath") xpath: String? = null, title: String? = null, action: DialogUiComponent.() -> Unit = {}): DialogUiComponent {
//  val dialogXpath = when {
//    xpath != null -> xpath
//    title != null -> "//div[@title='$title']"
//    else -> "//div[@class='MyDialog']"
//  }
//  return x(dialogXpath, DialogUiComponent::class.java).apply(action)
//}
//
//fun Finder.newDockerConnectionDialog(locator: QueryBuilder.() -> String, action: DialogUiComponent.() -> Unit = {}): DialogUiComponent {
//  return x(DialogUiComponent::class.java) { locator() }.apply(action)
//}
//
//fun Finder.isNewDockerConnectionDialogOpened(@Language("xpath") xpath: String? = null) =
//  xx(xpath ?: "//div[@class='MyDialog']", DialogUiComponent::class.java).list().isNotEmpty()
//
//fun Finder.newDockerConnectionDialog(@Language("xpath") xpath: String? = null, action: DialogUiComponent.() -> Unit) =
//  x(xpath ?: "//div[@class='MyDialog']", DialogUiComponent::class.java).action()
//




fun Finder.newDockerConnectionDialog(action: NewDockerConnectionDialogUI.() -> Unit) {
  x("//div[@title='Docker2']", NewDockerConnectionDialogUI::class.java).action()
}

fun Driver.newDockerConnectionDialog(action: NewDockerConnectionDialogUI.() -> Unit) {
  this.ui.newDockerConnectionDialog(action)
}

open class NewDockerConnectionDialogUI(data: ComponentData) : UiComponent(data) {
  fun setDockerConnectionName(text: String) {
    nameTextField.doubleClick()
    keyboard {
      backspace()
      driver.ui.pasteText(text)
    }
  }


  val nameTextField = textField("//div[@accessiblename='Name:' and @class='JBTextField']")
  val okButton = x { byAccessibleName("OK") }


}