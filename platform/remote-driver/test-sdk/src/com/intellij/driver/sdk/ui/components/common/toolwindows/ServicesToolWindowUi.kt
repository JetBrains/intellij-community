package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.accessibleTree
import com.intellij.driver.sdk.ui.components.elements.button

fun IdeaFrameUI.servicesToolWindow(action: ServicesToolWindowUi.() -> Unit = {}): ServicesToolWindowUi =
  x(ServicesToolWindowUi::class.java) { byAccessibleName("Services Tool Window") }.apply(action)

class ServicesToolWindowUi(data: ComponentData): ToolWindowUiComponent(data) {

  val servicesTree = accessibleTree { byJavaClass("com.intellij.platform.execution.serviceView.ServiceViewTree") }
  val addServiceButton = x("//div[@tooltiptext='Add Service' and @class='ActionButton']")

  fun runnerTabs(block: RunnerTabsUiComponent.() -> Unit = {}): RunnerTabsUiComponent = x(RunnerTabsUiComponent::class.java) {
    byType("com.intellij.execution.ui.layout.impl.JBRunnerTabs")
  }.apply(block)

  class RunnerTabsUiComponent(data: ComponentData) : UiComponent(data) {
    val serverTab = tabLabel("Server")
    val consoleTab = tabLabel("Console")
    val threadsAndVariablesTab = tabLabel("Threads & Variables")
    val stopButton = x { contains(byAccessibleName("Stop")) }
    val runButton = x { byAccessibleName("Run") }
    val beansTab = tabLabel("Beans")
    val healthTab = tabLabel("Health")
    val mappingsTab = tabLabel("Mappings")
    val stepOverButton get() = button { byAccessibleName("Step Over") }

    private fun tabLabel(name: String) = x { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName(name)) }
  }
}