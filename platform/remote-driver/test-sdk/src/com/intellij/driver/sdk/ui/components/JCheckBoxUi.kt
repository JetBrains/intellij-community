package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import org.intellij.lang.annotations.Language

fun Finder.checkBox(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JCheckBox']",
                                                                  JCheckBoxUi::class.java)

fun Finder.checkBox(locator: QueryBuilder.() -> String) = x(JCheckBoxUi::class.java) {locator()}

class JCheckBoxUi(data: ComponentData) : UiComponent(data) {
  private val checkboxComponent by lazy { driver.cast(component, JCheckBox::class) }

  val text by lazy {
    checkboxComponent.getText()
  }

  fun check() {
    if (!isSelected()) {
      click()
    }
  }

  fun uncheck() {
    if (isSelected()) {
      click()
    }
  }

  fun isSelected() = checkboxComponent.isSelected()
}

@Remote("javax.swing.JCheckBox", plugin = REMOTE_ROBOT_MODULE_ID)
interface JCheckBox {
  fun isSelected(): Boolean
  fun getText(): String
}