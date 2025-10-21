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
    val serverTab = x { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName("Server")) }
    val threadsAndVariablesTab = x { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName("Threads & Variables")) }
    val stopButton = x { contains(byAccessibleName("Stop")) }
    val runButton = x { byAccessibleName("Run") }
    val beansTab = x { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName("Beans")) }
    val healthTab = x { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName("Health")) }
    val mappingsTab = x { and(byType("com.intellij.ui.tabs.impl.TabLabel"), byAccessibleName("Mappings")) }
    val stepOverButton get() = button { byAccessibleName("Step Over") }
  }
}