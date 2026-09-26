package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.coverageToolWindow(action: CoverageToolWindowUI.() -> Unit = {}): CoverageToolWindowUI =
  x(CoverageToolWindowUI::class.java) { byClass("CoverageView") }.apply(action)

class CoverageToolWindowUI(data: ComponentData) : UiComponent(data) {
  val reportTable: UiComponent = x { byClass("JBTreeTable") }
}
