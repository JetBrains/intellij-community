package com.intellij.driver.sdk.ui.components.python

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI

fun IdeaFrameUI.jupyterVariables(func:JupyterVariablesUi.() -> Unit = {}) =
  x(JupyterVariablesUi::class.java) { componentWithChild(byClass("InternalDecoratorImpl"), byAccessibleName("Jupyter Variables"))}.apply(func)

class JupyterVariablesUi(data: ComponentData) : UiComponent(data)