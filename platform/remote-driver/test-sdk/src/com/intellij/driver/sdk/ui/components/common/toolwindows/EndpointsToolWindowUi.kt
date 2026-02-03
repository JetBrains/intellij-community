package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.endpointsToolWindow(action: EndpointsToolWindowUi.() -> Unit = {}): EndpointsToolWindowUi =
  x(EndpointsToolWindowUi::class.java) {
    componentWithChild(
      byClass("InternalDecoratorImpl"),
      byAccessibleName("Endpoints")
    )
  }.apply(action)

class EndpointsToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data)