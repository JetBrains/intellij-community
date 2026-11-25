package com.intellij.driver.sdk.notebooks

import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.ToolWindowUiComponent

fun IdeaFrameUI.jupyterToolWindow(action: ToolWindowUiComponent.() -> Unit = {}): ToolWindowUiComponent =
  x(ToolWindowUiComponent::class.java) {
    componentWithChild(byType("com.intellij.toolWindow.InternalDecoratorImpl"), byAccessibleName("Jupyter"))
  }.apply(action)
