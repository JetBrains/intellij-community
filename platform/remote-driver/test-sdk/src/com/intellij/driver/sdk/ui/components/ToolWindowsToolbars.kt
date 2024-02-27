package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators

class ToolWindowLeftToolbarUi(data: ComponentData) : UiComponent(data) {
  val buildButton: StripeButtonUi
    get() = stripeButton(Locators.byAccessibleName("Build"))
}

class ToolWindowRightToolbarUi(data: ComponentData) : UiComponent(data) {
  val notificationsButton: StripeButtonUi
    get() = stripeButton(Locators.byAccessibleName("Notifications"))
  val gradleButton: StripeButtonUi
    get() = stripeButton(Locators.byAccessibleName("Gradle"))
  val mavenButton: StripeButtonUi
    get() = stripeButton(Locators.byAccessibleName("Maven"))
}

class StripeButtonUi(data: ComponentData) : UiComponent(data) {
  private val button: StripeButtonComponent by lazy {
    driver.cast(component, StripeButtonComponent::class)
  }

  fun isSelected() = driver.withContext(OnDispatcher.EDT) {
    button.isSelected()
  }

  fun open() {
    if (!isSelected()) {
      click()
    }
  }

  @Remote("com.intellij.openapi.wm.impl.SquareStripeButton")
  interface StripeButtonComponent {
    fun isSelected(): Boolean
  }
}

private fun Finder.stripeButton(locator: String) = x(locator, StripeButtonUi::class.java)
