package com.intellij.driver.sdk.ui.components.elements

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent

class LetsPlotComponent(data: ComponentData) : UiComponent(data) {
  private val plot by lazy { driver.cast(component, LetsPlotComponentRef::class) }

  val htmlSource
    get() = plot.getPlotHtml()
}

@Remote("com.intellij.kotlin.jupyter.plots.LetsPlotComponent", plugin = "org.jetbrains.plugins.kotlin.jupyter/intellij.kotlin.jupyter.plots")
interface LetsPlotComponentRef {
  fun getPlotHtml(): String
}