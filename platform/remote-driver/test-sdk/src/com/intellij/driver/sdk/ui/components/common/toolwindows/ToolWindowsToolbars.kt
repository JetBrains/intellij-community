package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

open class ToolWindowToolbarUi(data: ComponentData) : UiComponent(data) {
  fun stripeButton(locator: QueryBuilder.() -> String): StripeButtonUi = x(StripeButtonUi::class.java, locator)
  fun stripeButton(accessibleName: String): StripeButtonUi = stripeButton { byAccessibleName(accessibleName) }
}

class ToolWindowLeftToolbarUi(data: ComponentData) : ToolWindowToolbarUi(data) {
  val projectButton: StripeButtonUi = stripeButton("Project")
  val runButton: StripeButtonUi = stripeButton("Run")
  val buildButton: StripeButtonUi = stripeButton("Build")
  val gitButton: StripeButtonUi = stripeButton("Git")
  val commitButton: StripeButtonUi = stripeButton("Commit")
  val structureButton: StripeButtonUi = stripeButton("Structure")
  val servicesButton: StripeButtonUi = stripeButton("Services")
  val terminalButton: StripeButtonUi = stripeButton("Terminal")
  val problemsButton: StripeButtonUi = stripeButton("Problems")
  val moreButton: StripeButtonUi = stripeButton("More")
  val debugButton: StripeButtonUi = stripeButton("Debug")
  val findButton: StripeButtonUi = stripeButton("Find")
  val cmakeButton: StripeButtonUi = stripeButton("CMake")
  val profilerButton: StripeButtonUi = stripeButton("Profiler")
  val jpaButton: StripeButtonUi = stripeButton("JPA Console")
  val persistenceButton: StripeButtonUi = stripeButton("Persistence")
  val valgrindButton: StripeButtonUi = stripeButton("Run Valgrind Memcheck")

  fun openMoreToolWindow() { moreButton.click() }
}

class ToolWindowRightToolbarUi(data: ComponentData) : ToolWindowToolbarUi(data) {
  val notificationsButton: StripeButtonUi = stripeButton("Notifications")
  val gradleButton: StripeButtonUi = stripeButton("Gradle")
  val mavenButton: StripeButtonUi = stripeButton("Maven")
  val databaseButton: StripeButtonUi = stripeButton("Database")
  val aiAssistantButton: StripeButtonUi = stripeButton("AI Chat")
  val mesonButton: StripeButtonUi = stripeButton("Meson")
}

class StripeButtonUi(data: ComponentData) : UiComponent(data) {
  val button: StripeButtonComponent
    get() = driver.cast(component, StripeButtonComponent::class)

  fun isSelected(): Boolean = driver.withContext(OnDispatcher.EDT) {
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
      driver.invokeAction(activateToolWindowAction, component = component)
    }
  }

  fun toolwindowIsPresented(): Boolean {
    return button.getToolWindow().isVisible()
  }

  fun close() {
    driver.withContext(OnDispatcher.EDT) {
      button.getToolWindow().hide()
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
    fun stretchWidth(value: Int)
    fun hide()
    fun stretchHeight(value: Int)
  }

  @Remote($$"com.intellij.ide.actions.ActivateToolWindowAction$Manager")
  interface ActivateToolWindowActionManager {
    fun getActionIdForToolWindow(id: String): String
  }
}