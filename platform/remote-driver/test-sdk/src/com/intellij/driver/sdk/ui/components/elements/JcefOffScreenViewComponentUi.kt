package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

class JcefOffScreenViewComponent(data: ComponentData) : UiComponent(data) {
  private val browser by lazy { driver.cast(component, JcefOffScreenViewComponentRef::class) }

  val htmlSource
    get() = browser.getSource()
}

@Remote("com.intellij.jupyter.core.jupyter.editor.outputs.webOutputs.offscreen.raster.JcefOffScreenViewComponent", plugin = "intellij.jupyter/intellij.jupyter.core")
interface JcefOffScreenViewComponentRef {
  fun getSource(): String
}