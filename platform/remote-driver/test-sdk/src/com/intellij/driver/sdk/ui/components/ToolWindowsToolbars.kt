package com.intellij.driver.sdk.ui.components

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.Finder
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.should

class ToolWindowLeftToolbarUi(data: ComponentData) : UiComponent(data) {
  val projectButton = stripeButton { byAccessibleName("Project") }
  val runButton = stripeButton { byAccessibleName("Run") }
  val buildButton = stripeButton { byAccessibleName("Build") }
  val gitButton = stripeButton { byAccessibleName("Git") }
  val commitButton = stripeButton { byAccessibleName("Commit") }
  val structureButton = stripeButton { byAccessibleName("Structure") }
  val servicesButton = stripeButton { byAccessibleName("Services") }
  val terminalButton = stripeButton { byAccessibleName("Terminal") }
  val problemsButton = stripeButton { byAccessibleName("Problems") }
  val moreButton = stripeButton { byAccessibleName("More") }
  val debugButton = stripeButton { byAccessibleName("Debug") }
}

class ToolWindowRightToolbarUi(data: ComponentData) : UiComponent(data) {
  val notificationsButton = stripeButton { byAccessibleName("Notifications") }
  val gradleButton = stripeButton { byAccessibleName("Gradle") }
  val mavenButton = stripeButton { byAccessibleName("Maven") }
  val databaseButton = stripeButton { byAccessibleName("Database") }
  val aiAssistantButton = stripeButton { byAccessibleName("AI Assistant") }
  val mesonButton = stripeButton { byAccessibleName("Meson") }
}

class StripeButtonUi(data: ComponentData) : UiComponent(data) {
  private val button: StripeButtonComponent
    get() = driver.cast(component, StripeButtonComponent::class)

  fun isSelected() = driver.withContext(OnDispatcher.EDT) {
    button.isSelected()
  }

  fun isToolWindowVisible(): Boolean {
    return button.getToolWindow().isVisible()
  }

  fun open() {
    val toolWindow = button.getToolWindow()
    if (!toolWindow.isActive()) {
      val activateToolWindowAction = driver.utility(ActivateToolWindowActionManager::class)
        .getActionIdForToolWindow(toolWindow.getId())
      driver.invokeAction(activateToolWindowAction)
    }
  }

  fun close() {
    val toolWindow = button.getToolWindow()
    if (toolWindow.isVisible()) {
      this.click()
      should ("Tool window is still visible"){ !toolWindow.isVisible() }
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
    fun isVisible(): Boolean
  }

  @Remote("com.intellij.ide.actions.ActivateToolWindowAction\$Manager")
  interface ActivateToolWindowActionManager {
    fun getActionIdForToolWindow(id: String): String
  }
}

private fun Finder.stripeButton(locator: String) = x(locator, StripeButtonUi::class.java)
private fun Finder.stripeButton(locator: QueryBuilder.() -> String) = x(StripeButtonUi::class.java) { locator() }
