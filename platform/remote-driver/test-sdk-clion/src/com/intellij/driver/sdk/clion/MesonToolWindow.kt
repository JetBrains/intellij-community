package com.intellij.driver.sdk.clion

import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.mesonToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) {
    componentWithChild(byType("com.intellij.toolWindow.InternalDecoratorImpl"), byClass("MesonToolWindowPanel"))
  }.apply(action)
