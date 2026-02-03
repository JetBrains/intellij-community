package com.intellij.driver.sdk.ui.components.idea.window

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.UiComponent.Companion.waitFound
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.DebugToolWindowUi
import kotlin.time.Duration.Companion.minutes

fun IdeaFrameUI.springDebuggerWindow(func: SpringDebuggerWindow.() -> Unit = {}): SpringDebuggerWindow =
  x(SpringDebuggerWindow::class.java)
  { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Threads & Variables")) }
    .waitFound(timeout = 5.minutes)
    .apply(func)

class SpringDebuggerWindow(data: ComponentData) : DebugToolWindowUi(data) {

  val beansTab: UiComponent = defineDebuggerTabByAccessibleName("Beans")

  val healthTab: UiComponent = defineDebuggerTabByAccessibleName("Health")
  val mappingsTab: UiComponent = defineDebuggerTabByAccessibleName("Mappings")
  val environmentTab: UiComponent = defineDebuggerTabByAccessibleName("Environment, JSON file")

  var springDefaultTabs: Map<String, UiComponent> = defaultTabs.apply {
    putIfAbsent("Beans", beansTab)
    putIfAbsent("Health", healthTab)
    putIfAbsent("Mappings", mappingsTab)
    putIfAbsent("Environment, JSON file", environmentTab)
  }
}