package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.Locators

class ToolWindowLeftToolbarUi(data: ComponentData) : UiComponent(data) {
  val projectButton = stripeButton(Locators.byAccessibleName("Project"))
  val buildButton = stripeButton(Locators.byAccessibleName("Build"))
  val gitButton = stripeButton(Locators.byAccessibleName("Git"))
}

class ToolWindowRightToolbarUi(data: ComponentData) : UiComponent(data) {
  val notificationsButton = stripeButton(Locators.byAccessibleName("Notifications"))
  val gradleButton = stripeButton(Locators.byAccessibleName("Gradle"))
  val mavenButton = stripeButton(Locators.byAccessibleName("Maven"))
}

class StripeButtonUi(data: ComponentData) : UiComponent(data) {
  private val button: StripeButtonComponent by lazy {
    driver.cast(component, StripeButtonComponent::class)
  }

  fun isSelected() = driver.withContext(OnDispatcher.EDT) {
    button.isSelected()
  }

  fun open() {
    val toolWindow = button.getToolWindow()
    if (!toolWindow.isActive()) {
      val activateToolWindowAction = driver.utility(ActivateToolWindowActionManager::class)
        .getActionIdForToolWindow(toolWindow.getId())
      driver.invokeAction(activateToolWindowAction)
    }
  }

  @Remote("com.intellij.openapi.wm.impl.SquareStripeButton")
  interface StripeButtonComponent {
    fun getToolWindow(): ToolWindowRef
    fun isSelected(): Boolean
  }

  @Remote("com.intellij.openapi.wm.impl.ToolWindowImpl")
  interface ToolWindowRef {
    fun getId(): String
    fun isActive(): Boolean
  }

  @Remote("com.intellij.ide.actions.ActivateToolWindowAction\$Manager")
  interface ActivateToolWindowActionManager {
    fun getActionIdForToolWindow(id: String): String
  }
}

private fun Finder.stripeButton(locator: String) = x(locator, StripeButtonUi::class.java)
