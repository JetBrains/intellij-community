package com.intellij.driver.sdk.ui.components.common.dialogs

import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.*
import com.intellij.driver.sdk.ui.shouldBe
import com.intellij.driver.sdk.ui.xQuery

fun Finder.customizeFloatingToolbarDialog(action: CustomizeFloatingToolbarDialog.() -> Unit) {
  x(xQuery { byTitle("Customize Floating Code Toolbar") }, CustomizeFloatingToolbarDialog::class.java).action()
}

class CustomizeFloatingToolbarDialog(data: ComponentData) : DialogUiComponent(data) {
  val addButton = button("Add…")

  fun addAction(actionName: String, customIcon: String? = null, vararg node: String) {
    tree().clickPath(*node, fullMatch = false)
    addButton.click()
    dialog(xQuery { byTitle("Add Action") }) {
      textField().appendText(actionName)
      shouldBe("${actionName} action is highlighted") {
        tree().collectSelectedPaths().single().path.last().contains(actionName.split(" ").first())
      }
      if (customIcon != null) {
        x(xQuery { byClass("BasicArrowButton") }).click()
        list().clickItem(customIcon)
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