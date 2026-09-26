package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.remoteDev.BeControlBuilder
import com.intellij.driver.sdk.remoteDev.BeControlClass
import com.intellij.driver.sdk.remoteDev.BeControlComponentBase
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.accessibleName
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.remote.Component
import com.intellij.driver.sdk.ui.remote.REMOTE_ROBOT_MODULE_ID
import com.intellij.driver.sdk.waitFor
import org.intellij.lang.annotations.Language
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun Finder.checkBox(@Language("xpath") xpath: String? = null) = x(xpath ?: "//div[@class='JCheckBox']",
                                                                  JCheckBoxUi::class.java)

fun Finder.checkBox(readableName: String? = null, locator: QueryBuilder.() -> String) = x(JCheckBoxUi::class.java, readableName) {locator()}

fun Finder.checkBoxWithName(name: String) = x(JCheckBoxUi::class.java) { byAccessibleName(name) }

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

  fun isSelected(): Boolean = checkboxComponent.isSelected()
}

fun JCheckBoxUi.waitSelected(selected: Boolean, timeout: Duration = 5.seconds) {
  waitFound()
  waitFor("'${accessibleName}' checkbox is ${if (selected) "selected" else "not selected"}", timeout) {
    isSelected() == selected
  }
}

@Remote("javax.swing.JCheckBox", plugin = REMOTE_ROBOT_MODULE_ID)
@BeControlClass(JCheckBoxComponentClassBuilder::class)
interface JCheckBox {
  fun isSelected(): Boolean
  fun getText(): String
}

class JCheckBoxComponentClassBuilder : BeControlBuilder {
  override fun build(driver: Driver, frontendComponent: Component, backendComponent: Component): Component {
    return JCheckBoxBeControl(driver, frontendComponent, backendComponent)
  }
}

class JCheckBoxBeControl(
  driver: Driver,
  frontendComponent: Component,
  backendComponent: Component,
) : BeControlComponentBase(driver, frontendComponent, backendComponent), JCheckBox {
  private val frontendCheckBox: JCheckBox by lazy {
    driver.cast(onFrontend { byType("javax.swing.JCheckBox") }.component, JCheckBox::class)
  }

  private val backendCheckBox: JCheckBox by lazy {
    driver.cast(backendComponent, JCheckBox::class)
  }

  /** A click toggles the frontend check box at once. The backend gets the new state later. */
  override fun isSelected(): Boolean = frontendCheckBox.isSelected()

  /**
   * The frontend shows a check box with a mnemonic as an empty check box and a separate label.
   * See `com.jetbrains.rd.ui.bindable.views.CheckBoxViewControl.initWrapped`.
   * The backend keeps the text, so the text is the same as in the monolith.
   */
  override fun getText(): String = backendCheckBox.getText()
}
