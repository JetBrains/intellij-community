package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.*
import com.intellij.driver.sdk.ui.should
import com.intellij.driver.sdk.ui.xQuery
import java.awt.Point

fun Finder.customizeFloatingToolbarDialog(action: CustomizeDialog.() -> Unit) {
  x(xQuery { byTitle("Customize Floating Code Toolbar") }, CustomizeDialog::class.java).action()
}

fun Finder.customizeMainToolbarDialog(action: CustomizeDialog.() -> Unit) {
  x(xQuery { byTitle("Customize Main Toolbar") }, CustomizeDialog::class.java).action()
}

class CustomizeDialog(data: ComponentData) : DialogUiComponent(data) {
  val addButton = button("Add…")

  fun addAction(actionName: String, customIcon: String? = null, vararg node: String) {
    tree().clickPath(*node, fullMatch = false)
    addButton.click()
    dialog(xQuery { byTitle("Add Action") }) {
      textField(xQuery { byAccessibleName("Message text filter") }).appendText(actionName)
      tree().should("${actionName} action is highlighted") {
        collectSelectedPaths().single().path.last().contains(actionName.split(" ").first())
      }
      if (customIcon != null) {
        if (customIcon.contains("/")) {
          val arrowButton = x(xQuery { byClass("BasicArrowButton") })
          arrowButton.click(point = Point(-arrowButton.component.width / 2, arrowButton.component.height / 2))
          dialog(title = "Browse Icon") {
            textField().text = customIcon
            okButton.click()
          }
        }
        else {
          x { byClass("BasicArrowButton") }.click()
          list().clickItem(customIcon)
        }
      }
      button("OK").click()
    }
  }

  fun deleteItem(vararg itemPath: String) {
    tree().clickPath(*itemPath, fullMatch = false)
    x(xQuery { byAccessibleName("Remove") }).click()
  }

  fun getItemRow(itemName: String) = tree().collectExpandedPaths().singleOrNull {
    it.path.last().toString() == itemName
  }?.row ?: error("Can't find row: $itemName")

}