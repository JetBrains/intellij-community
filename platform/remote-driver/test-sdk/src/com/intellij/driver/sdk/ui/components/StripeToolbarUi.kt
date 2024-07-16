package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.ui
import org.intellij.lang.annotations.Language

fun Finder.leftToolBar(@Language("xpath") xpath: String = "//div[@class='ToolWindowLeftToolbar']") =
  x(xpath, ToolWindowToolbarUiComponent::class.java)

fun Finder.rightToolBar(@Language("xpath") xpath: String = "//div[@class='ToolWindowRightToolbar']") =
  x(xpath, ToolWindowToolbarUiComponent::class.java)

class ToolWindowToolbarUiComponent(data: ComponentData) : UiComponent(data) {

  val moreButton
    get() = x("//div[@class='MoreSquareStripeButton']", UiComponent::class.java)

  fun openToolWindowFromMore(title: String) {
    moreButton.click()
    driver.ui.popup().findAllText().first { it.text == title }.click()
  }

  fun openToolWindow(title: String) {
    buttons().first { it.getTitle() == title }.apply {
      if (isSelected().not()) click()
    }
  }

  fun closeToolWindow(title: String) {
    buttons().first { it.getTitle() == title }.apply {
      if (isSelected()) click()
    }
  }

  private fun buttons() =
    xx("//div[@class='SquareStripeButton']", StripeButtonUiComponent::class.java).list()
}

class StripeButtonUiComponent(data: ComponentData) : UiComponent(data) {

  private val stripeButtonComponent by lazy { driver.cast(component, SquareStripeButtonRef::class) }

  fun getTitle() = stripeButtonComponent.toolWindow.getId()

  fun isSelected() = stripeButtonComponent.toolWindow.windowInfo.isVisible
}

@Remote("com.intellij.toolWindow.ToolWindowToolbar")
interface ToolWindowToolbarRef

@Remote("com.intellij.openapi.wm.impl.SquareStripeButton")
interface SquareStripeButtonRef {
  val toolWindow: ToolWindowImplRef
}

@Remote("com.intellij.openapi.wm.impl.ToolWindowImpl")
interface ToolWindowImplRef {
  fun getId(): String

  var windowInfo: WindowInfoRef
}

@Remote("com.intellij.openapi.wm.WindowInfo")
interface WindowInfoRef {
  val isVisible: Boolean
}