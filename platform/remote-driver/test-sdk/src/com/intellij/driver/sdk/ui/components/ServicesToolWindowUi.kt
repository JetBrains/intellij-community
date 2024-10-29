package com.intellij.driver.sdk.ui.components

import com.intellij.driver.sdk.ui.xQuery

fun IdeaFrameUI.servicesToolWindow(action: ServicesToolWindowUi.() -> Unit = {}): ServicesToolWindowUi =
  x(ServicesToolWindowUi::class.java) { byAccessibleName("Services Tool Window") }.apply(action)

class ServicesToolWindowUi(data: ComponentData): UiComponent(data) {

  val servicesTree = tree(xQuery { byJavaClass("com.intellij.platform.execution.serviceView.ServiceViewTree") })
}