package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import org.intellij.lang.annotations.Language

fun Finder.checkBox(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JCheckBox']",
                                                                  JCheckBoxUi::class.java)

class JCheckBoxUi(data: ComponentData) : UiComponent(data) {
  private val checkboxComponent by lazy { driver.cast(component, JCheckBox::class) }

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
}