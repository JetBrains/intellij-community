package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.profilerToolWindow(action: ProfilerToolWindowUi.() -> Unit = {}): ProfilerToolWindowUi =
  x(ProfilerToolWindowUi::class.java) {
    componentWithChild(
      byClass("InternalDecoratorImpl"),
      byAccessibleName("Profiler")
    )
  }.apply(action)

class ProfilerToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data)