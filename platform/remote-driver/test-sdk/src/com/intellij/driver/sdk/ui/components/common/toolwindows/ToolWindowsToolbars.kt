package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.client.Remote
import com.intellij.driver.model.OnDispatcher
import com.intellij.driver.sdk.ideLogger
import com.intellij.driver.sdk.ui.QueryBuilder
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.waitFor
import kotlin.time.Duration.Companion.seconds

private val TOOL_WINDOW_STATE_TIMEOUT = 30.seconds

open class ToolWindowToolbarUi(data: ComponentData) : UiComponent(data) {
  fun stripeButton(locator: QueryBuilder.() -> String): StripeButtonUi = x(StripeButtonUi::class.java, locator)
  fun stripeButton(accessibleName: String): StripeButtonUi = stripeButton { byAccessibleName(accessibleName) }
}

class ToolWindowLeftToolbarUi(data: ComponentData) : ToolWindowToolbarUi(data) {
  val projectButton: StripeButtonUi = stripeButton("Project")
  val runButton: StripeButtonUi = stripeButton("Run")
  val buildButton: StripeButtonUi = stripeButton("Build")
  val gitButton: StripeButtonUi = stripeButton("Git")
  val vcsButton: StripeButtonUi = stripeButton("Version Control")
  val commitButton: StripeButtonUi = stripeButton("Commit")
  val structureButton: StripeButtonUi = stripeButton("Structure")
  val servicesButton: StripeButtonUi = stripeButton("Services")
  val terminalButton: StripeButtonUi = stripeButton("Terminal")
  val problemsButton: StripeButtonUi = stripeButton("Problems View")
  val moreButton: StripeButtonUi = stripeButton("More")
  val debugButton: StripeButtonUi = stripeButton("Debug")
  val findButton: StripeButtonUi = stripeButton("Find")
  val cmakeButton: StripeButtonUi = stripeButton("CMake")
  val westButton: StripeButtonUi = stripeButton("West")
  val profilerButton: StripeButtonUi = stripeButton("Profiler")
  val jpaButton: StripeButtonUi = stripeButton("JPA Console")
  val persistenceButton: StripeButtonUi = stripeButton("Persistence")
  val valgrindButton: StripeButtonUi = stripeButton("Run Valgrind Memcheck")
  val vcpkg: StripeButtonUi = stripeButton("Vcpkg")
  fun openMoreToolWindow() {
    moreButton.click()
  }
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
    val id = toolWindow.getId()
    driver.ideLogger.info("Opening tool window: id=$id, active=${toolWindow.isActive()}, visible=${toolWindow.isVisible()}")
    driver.withContext(OnDispatcher.EDT) {
      toolWindow.activate(runnable = null, autoFocusContents = true, forced = true)
    }
    waitFor("Tool window '$id' is visible", timeout = TOOL_WINDOW_STATE_TIMEOUT) {
      toolWindow.isVisible()
    }
  }

  fun close() {
    val toolWindow = button.getToolWindow()
    val id = toolWindow.getId()
    driver.withContext(OnDispatcher.EDT) {
      toolWindow.hide()
    }
    waitFor("Tool window '$id' is hidden", timeout = TOOL_WINDOW_STATE_TIMEOUT) {
      !toolWindow.isVisible()
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

    /**
     * Shows the tool window. The call never hides it, so a repeated call is safe.
     */
    fun activate(runnable: Runnable?, autoFocusContents: Boolean, forced: Boolean)
    fun stretchWidth(value: Int)
    fun hide()
    fun stretchHeight(value: Int)
  }
}
