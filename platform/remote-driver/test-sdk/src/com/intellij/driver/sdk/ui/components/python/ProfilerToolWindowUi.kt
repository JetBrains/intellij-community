package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.profilerToolWindow(func: ProfilerToolWindowUi.() -> Unit = {}) =
  x(ProfilerToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Profiler")) }.apply(func)

class ProfilerToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data)