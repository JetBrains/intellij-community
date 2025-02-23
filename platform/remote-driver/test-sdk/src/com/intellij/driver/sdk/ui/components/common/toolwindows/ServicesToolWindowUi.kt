package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.elements.tree
import com.intellij.driver.sdk.ui.xQuery

fun IdeaFrameUI.servicesToolWindow(action: ServicesToolWindowUi.() -> Unit = {}): ServicesToolWindowUi =
  x(ServicesToolWindowUi::class.java) { byAccessibleName("Services Tool Window") }.apply(action)

class ServicesToolWindowUi(data: ComponentData): UiComponent(data) {

  val servicesTree = tree(xQuery { byJavaClass("com.intellij.platform.execution.serviceView.ServiceViewTree") })
  val addServiceButton = x("//div[@tooltiptext='Add Service' and @class='ActionButton']")
}