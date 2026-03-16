package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI


fun IdeaFrameUI.plotsToolWindow(func: ConcurrencyToolWindowUi.() -> Unit = {}) =
  x(ConcurrencyToolWindowUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Plots")) }.apply(func)
