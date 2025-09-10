package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.runProfilerToolWindow(func: RunProfilerToolWindowUi.() -> Unit = {}) =
  x(RunProfilerToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Profile")) }.apply(func)

class RunProfilerToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data)