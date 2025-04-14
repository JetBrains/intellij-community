package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.invokeAction
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.accessibleList
import com.intellij.driver.sdk.ui.components.elements.popup

open class ToolWindowToolbarUi(data: ComponentData) : UiComponent(data) {
  fun stripeButton(locator: QueryBuilder.() -> String) = x(StripeButtonUi::class.java, locator)
  fun stripeButton(accessibleName: String) = stripeButton { byAccessibleName(accessibleName) }
}

class ToolWindowLeftToolbarUi(data: ComponentData) : ToolWindowToolbarUi(data) {
  val projectButton = stripeButton("Project")
  val runButton = stripeButton("Run")
  val buildButton = stripeButton("Build")
  val gitButton = stripeButton("Git")
  val commitButton = stripeButton("Commit")
  val structureButton = stripeButton("Structure")
  val servicesButton = stripeButton("Services")
  val terminalButton = stripeButton("Terminal")
  val problemsButton = stripeButton("Problems")
  val jupyterButton = stripeButton("Jupyter")
  val moreButton = stripeButton("More")
  val debugButton = stripeButton("Debug")
  val findButton = stripeButton("Find")
  val cmakeButton = stripeButton("CMake")

  fun IdeaFrameUI.openMoreToolWindow(name: String) {
    moreButton.click()
    popup().accessibleList().clickItem(name, false)
  }
}

class ToolWindowRightToolbarUi(data: ComponentData) : ToolWindowToolbarUi(data) {
  val notificationsButton = stripeButton("Notifications")
  val gradleButton = stripeButton("Gradle")
  val mavenButton = stripeButton("Maven")
  val databaseButton = stripeButton("Database")
  val aiAssistantButton = stripeButton("AI Chat")
  val mesonButton = stripeButton("Meson")
}

class StripeButtonUi(data: ComponentData) : UiComponent(data) {
  val button: StripeButtonComponent
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

  @Remote("com.intellij.ide.actions.ActivateToolWindowAction\$Manager")
  interface ActivateToolWindowActionManager {
    fun getActionIdForToolWindow(id: String): String
  }
}