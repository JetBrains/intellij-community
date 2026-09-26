package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

class PlotsToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data)

fun IdeaFrameUI.plotsToolWindow(func: PlotsToolWindowUi.() -> Unit = {}) =
  x(PlotsToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Plots")) }.apply(func)
